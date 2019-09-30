// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gmos

import cats._
import cats.data.Kleisli
import cats.data.EitherT
import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.Concurrent
import gem.enum.LightSinkName
import gsp.math.Angle
import gsp.math.Offset
import gsp.math.syntax.string._
import edu.gemini.spModel.config2.ItemKey
import edu.gemini.spModel.gemini.gmos.GmosCommonType._
import edu.gemini.spModel.gemini.gmos.InstGmosCommon._
import edu.gemini.spModel.guide.StandardGuideOptions
import edu.gemini.spModel.obscomp.InstConstants.{EXPOSURE_TIME_PROP, _}
import edu.gemini.spModel.seqcomp.SeqConfigNames.{INSTRUMENT_KEY}
import edu.gemini.spModel.gemini.gmos.GmosCommonType
import io.chrisdavenport.log4cats.Logger
import java.lang.{Double => JDouble, Integer => JInt}

import scala.concurrent.duration._
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.Guiding
import seqexec.model.enum.ObserveCommandResult
import seqexec.model.enum.NodAndShuffleStage
import seqexec.model.enum.NodAndShuffleStage._
import seqexec.server.ConfigUtilOps.{ContentError, ConversionError, _}
import seqexec.server.gmos.Gmos.SiteSpecifics
import seqexec.server.gmos.GmosController.Config._
import seqexec.server.gmos.GmosController.Config.NSConfig
import seqexec.server.gmos.GmosController.SiteDependentTypes
import seqexec.server.keywords.{DhsInstrument, KeywordsClient}
import seqexec.server._
import seqexec.server.CleanConfig.extractItem
import squants.space.Length
import squants.{Seconds, Time}
import squants.space.LengthConversions._

abstract class Gmos[F[_]: MonadError[?[_], Throwable]: Concurrent: Logger, T <: GmosController.SiteDependentTypes]
(controller: GmosController[F, T], ss: SiteSpecifics[T])
(configTypes: GmosController.Config[T]) extends DhsInstrument[F] with InstrumentSystem[F] {
  import Gmos._
  import InstrumentSystem._

  override def sfName(config: CleanConfig): LightSinkName = LightSinkName.Gmos

  override val contributorName: String = "gmosdc"

  override val keywordsClient: KeywordsClient[F] = this

  val continueCommand: Time => F[ObserveCommandResult] =
    controller.resumePaused

  override val observeControl: InstrumentSystem.ObserveControl[F] = CompleteControl(
    StopObserveCmd(controller.stopObserve),
    AbortObserveCmd(controller.abortObserve),
    PauseObserveCmd(controller.pauseObserve),
    ContinuePausedCmd(continueCommand),
    StopPausedCmd(controller.stopPaused),
    AbortPausedCmd(controller.abortPaused)
  )

  protected def fpuFromFPUnit(n: Option[T#FPU], m: Option[String])(fpu: FPUnitMode): GmosFPU = fpu match {
    case FPUnitMode.BUILTIN     => configTypes.BuiltInFPU(n.getOrElse(ss.fpuDefault))
    case FPUnitMode.CUSTOM_MASK => m match {
      case Some(u) => GmosController.Config.CustomMaskFPU(u)
      case _       => GmosController.Config.UnknownFPU
    }
  }

  private def calcDisperser(disp: T#Disperser, order: Option[DisperserOrder], wl: Option[Length])
  : Either[ConfigUtilOps.ExtractFailure, configTypes.GmosDisperser] =
    if (configTypes.isMirror(disp)) {
      configTypes.GmosDisperser.Mirror.asRight[ConfigUtilOps.ExtractFailure]
    } else order.map { o =>
      if(o === GmosCommonType.Order.ZERO)
        configTypes.GmosDisperser.Order0(disp).asRight[ConfigUtilOps.ExtractFailure]
      else wl.map(w => configTypes.GmosDisperser.OrderN(disp, o, w)
        .asRight[ConfigUtilOps.ExtractFailure]).getOrElse(
          ConfigUtilOps.ContentError(s"Disperser order ${o.displayValue} is missing a wavelength.")
          .asLeft
        )
    }.getOrElse(ConfigUtilOps.ContentError(s"Disperser is missing an order.").asLeft)

  private def ccConfigFromSequenceConfig(config: CleanConfig): TrySeq[configTypes.CCConfig] =
    (for {
      filter           <- ss.extractFilter(config)
      disp             <- ss.extractDisperser(config)
      disperserOrder   =  config.extractInstAs[DisperserOrder](DISPERSER_ORDER_PROP)
      disperserLambda  =  config.extractInstAs[JDouble](DISPERSER_LAMBDA_PROP).map(_.toDouble.nanometers)
      fpuName          =  ss.extractFPU(config)
      fpuMask          =  config.extractInstAs[String](FPU_MASK_PROP)
      fpu              <- config.extractInstAs[FPUnitMode](FPU_MODE_PROP).map(fpuFromFPUnit(fpuName.toOption, fpuMask.toOption))
      stageMode        <- ss.extractStageMode(config)
      dtax             <- config.extractInstAs[DTAX](DTAX_OFFSET_PROP)
      adc              <- config.extractInstAs[ADC](ADC_PROP)
      electronicOffset =  config.extractInstAs[UseElectronicOffset](USE_ELECTRONIC_OFFSETTING_PROP)
      disperser        <- calcDisperser(disp, disperserOrder.toOption, disperserLambda.toOption)
      obsType          <- config.extractObsAs[String](OBSERVE_TYPE_PROP)
      isDarkOrBias = List(DARK_OBSERVE_TYPE, BIAS_OBSERVE_TYPE).exists(_ === obsType)
    } yield configTypes.CCConfig(filter, disperser, fpu, stageMode, dtax, adc, electronicOffset.toOption, isDarkOrBias))
      .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  private def fromSequenceConfig(config: CleanConfig): Either[SeqexecFailure, GmosController.GmosConfig[T]] =
    for {
      cc <- ccConfigFromSequenceConfig(config)
      dc <- dcConfigFromSequenceConfig(config)
      ns <- Gmos.nsConfig(config)
    } yield new GmosController.GmosConfig[T](configTypes)(cc, dc, ns)

  override def calcStepType(config: CleanConfig): Either[SeqexecFailure, StepType] = {
    val stdType = SequenceConfiguration.calcStepType(config)
    if (Gmos.isNodAndShuffle(config)) {
      stdType.flatMap {
        case StepType.DarkOrBias(_)      => StepType.DarkOrBiasNS(instrument).asRight
        case StepType.CelestialObject(_) => StepType.NodAndShuffle(instrument).asRight
        case st                          => SeqexecFailure.Unexpected(s"N&S is not supported for steps of type $st")
          .asLeft
      }
    } else {
      stdType
    }
  }

  override def observe(config: CleanConfig): Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      calcObserveTime(config).flatMap { x =>
        controller.observe(fileId, x)
      }
    }

  override def instrumentActions(config: CleanConfig): InstrumentActions[F] =
    new GmosInstrumentActions(this, config)

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Applicative[F].unit

  override def configure(config: CleanConfig): F[ConfigResult[F]] =
    EitherT.fromEither[F](fromSequenceConfig(config))
      .widenRethrowT
      .flatMap(controller.applyConfig)
      .as(ConfigResult(this))

  def configureShuffle(rows: Int): F[ConfigResult[F]] =
    controller.setRowsToShuffle(rows)
      .as(ConfigResult(this))

  override def calcObserveTime(config: CleanConfig): F[Time] =
    (Gmos.expTime[F](config), Gmos.nsConfigF[F](config)).mapN {(v, ns) =>
      v / ns.exposureDivider.toDouble
    }

  override def observeProgress(total: Time, elapsed: ElapsedTime): fs2.Stream[F, Progress] =
    controller
      .observeProgress(total, elapsed)
}

object Gmos {
  val name: String = INSTRUMENT_NAME_PROP

  // The sequence of nod and shuffle is always BAAB,
  // In principle we'd expect the OT to send the sequence but instead the
  // sequence is hardcoded in the seqexec and we only read the positions from
  // the OT
  val NsSequence: NonEmptyList[NodAndShuffleStage] =
    NonEmptyList.of(StageB, StageA, StageA, StageB)

  def rowsToShuffle(stage: NodAndShuffleStage, rows: Int): Int =
    if (stage === StageA) 0 else rows

  trait SiteSpecifics[T<:SiteDependentTypes] {
    def extractFilter(config: CleanConfig): Either[ExtractFailure, T#Filter]

    def extractDisperser(config: CleanConfig): Either[ExtractFailure, T#Disperser]

    def extractFPU(config: CleanConfig): Either[ExtractFailure, T#FPU]

    def extractStageMode(config: CleanConfig): Either[ExtractFailure, T#GmosStageMode]

    val fpuDefault: T#FPU
  }

  val NSKey: ItemKey = INSTRUMENT_KEY / USE_NS_PROP

  def isNodAndShuffle(config: CleanConfig): Boolean =
    config.extractAs[java.lang.Boolean](NSKey)
      .map(_.booleanValue())
      .getOrElse(false)

  private def configToAngle(s: String): Either[ExtractFailure, Angle] =
    s.parseDoubleOption
      .toRight(ContentError("Invalid offset value"))
      .map(Angle.fromDoubleArcseconds)

  private def extractGuiding(config: CleanConfig, k: ItemKey): Either[ExtractFailure, Guiding] =
    config
      .extractAs[StandardGuideOptions.Value](k)
      .flatMap(r => Guiding.fromString(r.toString).toRight(KeyNotFound(k)))
      .orElse {
        config.extractAs[String](k).flatMap(Guiding.fromString(_).toRight(KeyNotFound(k)))
      }

  private def nsPosition(config: CleanConfig, sc: Int): Either[ExtractFailure, Vector[NSPosition]] = {
    NodAndShuffleStage.NSStageEnumerated.all.slice(0, sc).map { s =>
      for {
        p <- config.extractInstAs[String](s"nsBeam${s.symbol.name}-p").flatMap(configToAngle).map(Offset.P.apply)
        q <- config.extractInstAs[String](s"nsBeam${s.symbol.name}-q").flatMap(configToAngle).map(Offset.Q.apply)
        k = INSTRUMENT_KEY / s"nsBeam${s.symbol.name}-guideWithOIWFS"
        g <- extractGuiding(config, k)
      } yield NSPosition(s, Offset(p, q), g)
    }.toVector.sequence
  }

  def nodAndShuffle(config: CleanConfig): Either[ExtractFailure, NSConfig.NodAndShuffle] =
    for {
      cycles <- config.extractInstAs[JInt](NUM_NS_CYCLES_PROP).map(_.toInt)
      rows   <- config.extractInstAs[JInt](DETECTOR_ROWS_PROP).map(_.toInt)
      sc     <- config.extractInstAs[JInt](NS_STEP_COUNT_PROP_NAME)
      pos    <- nsPosition(config, sc)
    } yield NSConfig.NodAndShuffle(cycles, rows, pos)

  def nsConfig(config: CleanConfig): TrySeq[NSConfig] =
    (for {
      useNS <- config.extractInstAs[java.lang.Boolean](USE_NS_PROP)
      ns    <- if (useNS) nodAndShuffle(config) else NSConfig.NoNodAndShuffle.asRight
    } yield ns).leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  def nsConfigF[F[_]: ApplicativeError[?[_], Throwable]](config: CleanConfig): F[NSConfig] =
    ApplicativeError[F, Throwable]
      .catchNonFatal(
        nsConfig(config).getOrElse(NSConfig.NoNodAndShuffle)
      )

  def expTime[F[_]: ApplicativeError[?[_], Throwable]](config: CleanConfig): F[Time] =
    ApplicativeError[F, Throwable]
      .catchNonFatal(
        config.extractObsAs[JDouble](EXPOSURE_TIME_PROP)
          .map(v => Seconds(v.toDouble))
          .getOrElse(Seconds(10000))
      )

  // It seems this is unused but it shows up on the DC apply config
  private def biasTimeObserveType(observeType: String): BiasTime = observeType match {
    case SCIENCE_OBSERVE_TYPE => BiasTime.BiasTimeUnset
    case FLAT_OBSERVE_TYPE    => BiasTime.BiasTimeUnset
    case ARC_OBSERVE_TYPE     => BiasTime.BiasTimeEmpty
    case DARK_OBSERVE_TYPE    => BiasTime.BiasTimeEmpty
    case BIAS_OBSERVE_TYPE    => BiasTime.BiasTimeUnset
    case _                    => BiasTime.BiasTimeUnset
  }

  private def shutterStateObserveType(observeType: String): ShutterState = observeType match {
    case SCIENCE_OBSERVE_TYPE => ShutterState.OpenShutter
    case FLAT_OBSERVE_TYPE    => ShutterState.OpenShutter
    case ARC_OBSERVE_TYPE     => ShutterState.OpenShutter
    case DARK_OBSERVE_TYPE    => ShutterState.CloseShutter
    case BIAS_OBSERVE_TYPE    => ShutterState.CloseShutter
    case _                    => ShutterState.UnsetShutter
  }

  private def customROIs(config: CleanConfig): List[ROI] = {
    def attemptROI(i: Int): Option[ROI] =
      (for {
        xStart <- config.extractInstAs[JInt](s"customROI${i}Xmin").map(_.toInt)
        xRange <- config.extractInstAs[JInt](s"customROI${i}Xrange").map(_.toInt)
        yStart <- config.extractInstAs[JInt](s"customROI${i}Ymin").map(_.toInt)
        yRange <- config.extractInstAs[JInt](s"customROI${i}Yrange").map(_.toInt)
      } yield new ROI(xStart, yStart, xRange, yRange)).toOption

    val rois = for {
      i <- 1 to 5
    } yield attemptROI(i)
    rois.toList.flattenOption
  }

  private def toGain(s: String): Either[ExtractFailure, Double] =
    s.parseDoubleOption
      .toRight(ConversionError(INSTRUMENT_KEY / AMP_GAIN_SETTING_PROP, "Bad Amp gain setting"))

  def dcConfigFromSequenceConfig(config: CleanConfig): TrySeq[DCConfig] =
    (for {
      obsType      <- config.extractObsAs[String](OBSERVE_TYPE_PROP)
      biasTime     <- biasTimeObserveType(obsType).asRight
      shutterState <- shutterStateObserveType(obsType).asRight
      exposureTime <- config.extractObsAs[JDouble](EXPOSURE_TIME_PROP).map(_.toDouble.seconds)
      ampReadMode  <- config.extractAs[AmpReadMode](AmpReadMode.KEY)
      gainChoice   <- config.extractInstAs[AmpGain](AMP_GAIN_CHOICE_PROP)
      ampCount     <- config.extractInstAs[AmpCount](AMP_COUNT_PROP)
      gainSetting  <- config.extractInstAs[String](AMP_GAIN_SETTING_PROP).flatMap(toGain)
      xBinning     <- config.extractInstAs[Binning](CCD_X_BIN_PROP)
      yBinning     <- config.extractInstAs[Binning](CCD_Y_BIN_PROP)
      builtInROI   <- config.extractInstAs[BuiltinROI](BUILTIN_ROI_PROP)
      customROI = if (builtInROI === BuiltinROI.CUSTOM) customROIs(config) else Nil
      roi          <- RegionsOfInterest.fromOCS(builtInROI, customROI).leftMap(e => ContentError(SeqexecFailure.explain(e)))
    } yield
      DCConfig(exposureTime, biasTime, shutterState, CCDReadout(ampReadMode, gainChoice, ampCount, gainSetting), CCDBinning(xBinning, yBinning), roi))
        .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

 }
