package edu.gemini.seqexec.server

import edu.gemini.seqexec.model.dhs.ImageFileId
import edu.gemini.seqexec.server.Flamingos2Controller._
import edu.gemini.seqexec.server.ConfigUtilOps._
import edu.gemini.spModel.config2.Config
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.Decker
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.Filter
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.ReadoutMode
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.Reads
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.WindowCover
import edu.gemini.spModel.obscomp.InstConstants.{ARC_OBSERVE_TYPE, CAL_OBSERVE_TYPE, BIAS_OBSERVE_TYPE, FLAT_OBSERVE_TYPE, SCIENCE_OBSERVE_TYPE, DARK_OBSERVE_TYPE, OBSERVE_TYPE_PROP}
import edu.gemini.spModel.gemini.flamingos2.Flamingos2._
import edu.gemini.spModel.seqcomp.SeqConfigNames._

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz._

final case class Flamingos2(f2Controller: Flamingos2Controller) extends Instrument {

  import Flamingos2._

  override val name: String = Flamingos2.name

  override val sfName: String = Flamingos2.sfName

  override val contributorName = "flamingos2"
  val dhsInstrumentName = "F2"

  override def observe(config: Config): SeqObserve[ImageFileId, ObserveResult] = Reader {
    fileId => f2Controller.observe(fileId).map(_ => ObserveResult(fileId))
  }

  override def configure(config: Config): SeqAction[ConfigResult] =
    fromSequenceConfig(config).flatMap(f2Controller.applyConfig).map(_ => ConfigResult(this))

}

object Flamingos2 {
  val name: String = INSTRUMENT_NAME_PROP

  val sfName: String = "f2"

  def fpuFromFPUnit(fpu: FPUnit):FocalPlaneUnit = fpu match {
    case FPUnit.FPU_NONE       => FocalPlaneUnit.Open
    case FPUnit.SUBPIX_PINHOLE => FocalPlaneUnit.GridSub1Pix
    case FPUnit.PINHOLE        => FocalPlaneUnit.Grid2Pix
    case FPUnit.LONGSLIT_1     => FocalPlaneUnit.Slit1Pix
    case FPUnit.LONGSLIT_2     => FocalPlaneUnit.Slit2Pix
    case FPUnit.LONGSLIT_3     => FocalPlaneUnit.Slit3Pix
    case FPUnit.LONGSLIT_4     => FocalPlaneUnit.Slit4Pix
    case FPUnit.LONGSLIT_6     => FocalPlaneUnit.Slit6Pix
    case FPUnit.LONGSLIT_8     => FocalPlaneUnit.Slit8Pix
    case FPUnit.CUSTOM_MASK    => FocalPlaneUnit.Custom("")
  }

  def readsFromReadMode(readMode: ReadMode): Flamingos2Controller.Reads = readMode match {
    case ReadMode.BRIGHT_OBJECT_SPEC => Reads.READS_1
    case ReadMode.MEDIUM_OBJECT_SPEC => Reads.READS_4
    case ReadMode.FAINT_OBJECT_SPEC  => Reads.READS_8
  }

  implicit def biasFromDecker(dk: Decker): BiasMode = dk match {
    case Decker.IMAGING   => BiasMode.Imaging
    case Decker.LONG_SLIT => BiasMode.LongSlit
    case Decker.MOS       => BiasMode.MOS
  }

  def fpuConfig(config: Config): \/[ConfigUtilOps.ExtractFailure, FocalPlaneUnit] = {
    val a = INSTRUMENT_KEY / FPU_PROP
    val b = INSTRUMENT_KEY / FPU_MASK_PROP

    config.extract(a).as[FPUnit].flatMap(x =>
      if(x != FPUnit.CUSTOM_MASK) \/-(fpuFromFPUnit(x))
      else config.extract(b).as[String].map(FocalPlaneUnit.Custom)
    )
  }

  def windowCoverFromObserveType(observeType: String): WindowCover = observeType match {
    case DARK_OBSERVE_TYPE => WindowCover.CLOSE
    case _                 => WindowCover.OPEN
  }

  implicit def grismFromSPDisperser(d: Disperser): Grism = d match {
    case Disperser.NONE    => Grism.Open
    case Disperser.R1200HK => Grism.R1200HK
    case Disperser.R1200JH => Grism.R1200JH
    case Disperser.R3000   => Grism.R3000
  }

  private def disperserFromObserveType(observeType: String, d: Disperser): Grism = observeType match {
    case DARK_OBSERVE_TYPE => Grism.Dark
    case _                 => d
  }

  private def exposureTime(config: Config): ExtractFailure \/ Duration =
    config.extract(OBSERVE_KEY / OBSERVE_TYPE_PROP).as[String].flatMap {
      case SCIENCE_OBSERVE_TYPE                                    =>
        config.extract(OBSERVE_KEY / EXPOSURE_TIME_PROP).as[java.lang.Double].map(_.toDouble.seconds)
      case BIAS_OBSERVE_TYPE | DARK_OBSERVE_TYPE                   =>
        config.extract(CALIBRATION_KEY / EXPOSURE_TIME_PROP).as[java.lang.Double].map(_.toDouble.seconds)
      case FLAT_OBSERVE_TYPE | ARC_OBSERVE_TYPE | CAL_OBSERVE_TYPE =>
        config.extract(CALIBRATION_KEY / EXPOSURE_TIME_PROP).as[java.lang.Double].map(_.toDouble.seconds)
      case obsType                                                 =>
        \/.left(ContentError(s"Unknown step type $obsType"))
    }

  def ccConfigFromSequenceConfig(config: Config): TrySeq[CCConfig] = ( for {
      obsType <- config.extract(OBSERVE_KEY / OBSERVE_TYPE_PROP).as[String]
      // WINDOW_COVER_PROP is optional. If not present, then window cover position is inferred from observe type.
      p       <- config.extract(INSTRUMENT_KEY / WINDOW_COVER_PROP).as[WindowCover].recover {
                   case _: ConfigUtilOps.ExtractFailure => windowCoverFromObserveType(obsType)
                 }
      q       <- config.extract(INSTRUMENT_KEY / DECKER_PROP).as[Decker]
      r       <- fpuConfig(config)
      s       <- config.extract(INSTRUMENT_KEY / FILTER_PROP).as[Filter]
      t       <- config.extract(INSTRUMENT_KEY / LYOT_WHEEL_PROP).as[LyotWheel]
      u       <- config.extract(INSTRUMENT_KEY / DISPERSER_PROP).as[Disperser].map(disperserFromObserveType(obsType, _))

    } yield CCConfig(p, q, r, s, t, u) ).leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  def dcConfigFromSequenceConfig(config: Config): TrySeq[DCConfig] = ( for {
    p <- exposureTime(config)
    // Reads is usually inferred from the read mode, but it can be explicit.
    q <- config.extract(OBSERVE_KEY / READS_PROP).as[Reads] match {
          case a: \/-[Reads] => a
          case _             => config.extract(INSTRUMENT_KEY / READMODE_PROP).as[ReadMode]
                                .map(readsFromReadMode)
        }
    // Readout mode defaults to SCIENCE if not present.
    r <- \/-(config.extract(INSTRUMENT_KEY / READOUT_MODE_PROP).as[ReadoutMode].getOrElse(ReadoutMode.SCIENCE))
    s <- config.extract(INSTRUMENT_KEY / DECKER_PROP).as[Decker]
  } yield DCConfig(p, q, r, s) ).leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  def fromSequenceConfig(config: Config): SeqAction[Flamingos2Config] = EitherT( Task ( for {
      p <- ccConfigFromSequenceConfig(config)
      q <- dcConfigFromSequenceConfig(config)
    } yield Flamingos2Config(p, q)
  ) )


}
