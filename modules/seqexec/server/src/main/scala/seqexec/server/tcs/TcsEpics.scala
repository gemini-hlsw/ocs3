// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.tcs

import cats.data.Nested
import cats.effect.{Async, IO, LiftIO, Sync}
import cats.implicits._
import squants.Angle
import edu.gemini.epics.acm._
import edu.gemini.seqexec.server.tcs.{BinaryEnabledDisabled, BinaryOnOff, BinaryYesNo}
import org.log4s.{Logger, getLogger}
import seqexec.model.enum.ApplyCommandResult
import seqexec.server.EpicsCommand._
import seqexec.server.EpicsUtil._
import seqexec.server.{EpicsCommand, EpicsSystem}
import squants.Time
import squants.space.Degrees
import squants.time.TimeConversions._

import scala.util.Try

/**
 * TcsEpics wraps the non-functional parts of the EPICS ACM library to interact with TCS. It has all the objects used
 * to read TCS status values and execute TCS commands.
 *
 * Created by jluhrs on 10/1/15.
 */

final class TcsEpics[F[_]: Async](epicsService: CaService, tops: Map[String, String]) {

  import TcsEpics._

  val TcsTop: String = tops.getOrElse("tcs", "")

  // This is a bit ugly. Commands are triggered from the main apply record, so I just choose an arbitrary command here.
  // Triggering that command will trigger all the marked commands.
  def post: F[ApplyCommandResult] = m1GuideCmd.post

  object m1GuideCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("m1Guide"))
    private val state = cs.map(_.getString("state"))

    def setState(v: String): F[Unit] = setParameter(state, v)
  }

  object m2GuideCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("m2Guide"))
    private val state = cs.map(_.getString("state"))

    def setState(v: String): F[Unit] = setParameter(state, v)
  }

  object m2GuideModeCmd extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("m2GuideMode"))

    private val coma = cs.map(_.getString("coma"))
    def setComa(v: String): F[Unit] = setParameter(coma, v)
  }

  object m2GuideConfigCmd extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("m2GuideConfig"))

    private val source = cs.map(_.getString("source"))
    def setSource(v: String): F[Unit] = setParameter(source, v)

    private val beam = cs.map(_.getString("beam"))
    def setBeam(v: String): F[Unit] = setParameter(beam, v)

    private val reset = cs.map(_.getString("reset"))
    def setReset(v: String): F[Unit] = setParameter(reset, v)
  }

  object mountGuideCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("mountGuide"))

    private val source = cs.map(_.getString("source"))

    def setSource(v: String): F[Unit] = setParameter(source, v)

    private val p1weight = cs.map(_.getDouble("p1weight"))

    def setP1Weight(v: Double): F[Unit] = setParameter[F, java.lang.Double](p1weight, v)

    private val p2weight = cs.map(_.getDouble("p2weight"))

    def setP2Weight(v: Double): F[Unit] = setParameter[F, java.lang.Double](p2weight, v)

    private val mode = cs.map(_.getString("mode"))

    def setMode(v: String): F[Unit] = setParameter(mode, v)
  }

  object offsetACmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("offsetPoA1"))

    private val x = cs.map(_.getDouble("x"))

    def setX(v: Double): F[Unit] = setParameter[F, java.lang.Double](x, v)

    private val y = cs.map(_.getDouble("y"))

    def setY(v: Double): F[Unit] = setParameter[F, java.lang.Double](y, v)
  }

  object offsetBCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("offsetPoB1"))

    private val x = cs.map(_.getDouble("x"))

    def setX(v: Double): F[Unit] = setParameter[F, java.lang.Double](x, v)

    private val y = cs.map(_.getDouble("y"))

    def setY(v: Double): F[Unit] = setParameter[F, java.lang.Double](y, v)
  }

  object offsetCCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("offsetPoC1"))

    private val x = cs.map(_.getDouble("x"))

    def setX(v: Double): F[Unit] = setParameter[F, java.lang.Double](x, v)

    private val y = cs.map(_.getDouble("y"))

    def setY(v: Double): F[Unit] = setParameter[F, java.lang.Double](y, v)
  }

  object wavelSourceA extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelSourceA"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object wavelSourceB extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelSourceB"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object wavelSourceC extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelSourceC"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object m2Beam extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("m2Beam"))

    private val beam = cs.map(_.getString("beam"))

    def setBeam(v: String): F[Unit] = setParameter(beam, v)
  }

  val pwfs1ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("pwfs1Guide", epicsService)

  val pwfs2ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("pwfs2Guide", epicsService)

  val oiwfsProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("oiwfsGuide", epicsService)

  val g1ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("g1Guide", epicsService)

  val g2ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("g2Guide", epicsService)

  val g3ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("g3Guide", epicsService)

  val g4ProbeGuideCmd: ProbeGuideCmd[F] = new ProbeGuideCmd("g4Guide", epicsService)

  val pwfs1ProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("p1Follow", epicsService)

  val pwfs2ProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("p2Follow", epicsService)

  val oiwfsProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("oiFollow", epicsService)

  val aoProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("aoFollow", epicsService)

  object pwfs1Park extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("pwfs1Park"))
  }

  object pwfs2Park extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("pwfs2Park"))
  }

  object oiwfsPark extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("oiwfsPark"))
  }

  object pwfs1StopObserveCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("pwfs1StopObserve"))
  }

  object pwfs2StopObserveCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("pwfs2StopObserve"))
  }

  object oiwfsStopObserveCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("oiwfsStopObserve"))
  }

  val pwfs1ObserveCmd: WfsObserveCmd[F] = new WfsObserveCmd("pwfs1Observe", epicsService)

  val pwfs2ObserveCmd: WfsObserveCmd[F] = new WfsObserveCmd("pwfs2Observe", epicsService)

  val oiwfsObserveCmd: WfsObserveCmd[F] = new WfsObserveCmd("oiwfsObserve", epicsService)

  object hrwfsParkCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("hrwfsPark"))
  }

  object hrwfsPosCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("hrwfs"))

    private val hrwfsPos = cs.map(_.getString("hrwfsPos"))

    def setHrwfsPos(v: String): F[Unit] = setParameter(hrwfsPos, v)
  }

  object scienceFoldParkCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("scienceFoldPark"))
  }

  object scienceFoldPosCmd extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("scienceFold"))

    private val scfold = cs.map(_.getString("scfold"))

    def setScfold(v: String): F[Unit] = setParameter(scfold, v)
  }

  object observe extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("tcs::observe"))
  }

  object endObserve extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("tcs::endObserve"))
  }

  object aoCorrect extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("aoCorrect"))

    private val correct = cs.map(_.getString("correct"))
    def setCorrections(v: String): F[Unit] = setParameter(correct, v)

    private val gains = cs.map(_.getInteger("gains"))
    def setGains(v: Int): F[Unit] = setParameter[F, java.lang.Integer](gains, v)

    private val matrix = cs.map(_.getInteger("matrix"))
    def setMatrix(v: Int): F[Unit] = setParameter[F, java.lang.Integer](matrix, v)
  }

  object aoPrepareControlMatrix extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("aoPrepareCm"))

    private val x = cs.map(_.getDouble("x"))
    def setX(v: Double): F[Unit] = setParameter[F, java.lang.Double](x, v)

    private val y = cs.map(_.getDouble("y"))
    def setY(v: Double): F[Unit] = setParameter[F, java.lang.Double](y, v)

    private val seeing = cs.map(_.getDouble("seeing"))
    def setSeeing(v: Double): F[Unit] = setParameter[F, java.lang.Double](seeing, v)

    private val starMagnitude = cs.map(_.getDouble("gsmag"))
    def setStarMagnitude(v: Double): F[Unit] = setParameter[F, java.lang.Double](starMagnitude, v)

    private val windSpeed = cs.map(_.getDouble("windspeed"))
    def setWindSpeed(v: Double): F[Unit] = setParameter[F, java.lang.Double](windSpeed, v)
  }

  object aoFlatten extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("aoFlatten"))
  }

  object aoStatistics extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("aoStats"))

    private val fileName = cs.map(_.getString("filename"))
    def setFileName(v: String): F[Unit] = setParameter(fileName, v)

    private val samples = cs.map(_.getInteger("samples"))
    def setSamples(v: Int): F[Unit] = setParameter[F, java.lang.Integer](samples, v)

    private val interval = cs.map(_.getDouble("interval"))
    def setInterval(v: Double): F[Unit] = setParameter[F, java.lang.Double](interval, v)

    private val triggerTime = cs.map(_.getDouble("trigtime"))
    def setTriggerTimeInterval(v: Double): F[Unit] = setParameter[F, java.lang.Double](triggerTime, v)
  }

  object targetFilter extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("filter1"))

    private val bandwidth = cs.map(_.getDouble("bandwidth"))
    def setBandwidth(v: Double): F[Unit] = setParameter[F, java.lang.Double](bandwidth, v)

    private val maxVelocity = cs.map(_.getDouble("maxv"))
    def setMaxVelocity(v: Double): F[Unit] = setParameter[F, java.lang.Double](maxVelocity, v)

    private val grabRadius = cs.map(_.getDouble("grab"))
    def setGrabRadius(v: Double): F[Unit] = setParameter[F, java.lang.Double](grabRadius, v)

    private val shortCircuit = cs.map(_.getString("shortCircuit"))
    def setShortCircuit(v: String): F[Unit] = setParameter(shortCircuit, v)
  }

  private val tcsState = epicsService.getStatusAcceptor("tcsstate")

  def absorbTipTilt: F[Option[Int]] = safeAttributeSInt(tcsState.getIntegerAttribute("absorbTipTilt"))

  def m1GuideSource: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m1GuideSource"))

  private val m1GuideAttr: CaAttribute[BinaryOnOff] = tcsState.addEnum("m1Guide",
    s"${TcsTop}im:m1GuideOn", classOf[BinaryOnOff], "M1 guide")
  def m1Guide: F[Option[BinaryOnOff]] = safeAttribute(m1GuideAttr)

  def m2p1Guide: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2p1Guide"))

  def m2p2Guide: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2p2Guide"))

  def m2oiGuide: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2oiGuide"))

  def m2aoGuide: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2aoGuide"))

  def comaCorrect: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("comaCorrect"))

  private val m2GuideStateAttr: CaAttribute[BinaryOnOff] = tcsState.addEnum("m2GuideState",
    s"${TcsTop}om:m2GuideState", classOf[BinaryOnOff], "M2 guiding state")
  def m2GuideState: F[Option[BinaryOnOff]] = safeAttribute(m2GuideStateAttr)

  def xoffsetPoA1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("xoffsetPoA1"))

  def yoffsetPoA1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("yoffsetPoA1"))

  def xoffsetPoB1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("xoffsetPoB1"))

  def yoffsetPoB1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("yoffsetPoB1"))

  def xoffsetPoC1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("xoffsetPoC1"))

  def yoffsetPoC1: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("yoffsetPoC1"))

  def sourceAWavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sourceAWavelength"))

  def sourceBWavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sourceBWavelength"))

  def sourceCWavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sourceCWavelength"))

  def chopBeam: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("chopBeam"))

  def p1FollowS: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("p1FollowS"))

  def p2FollowS: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("p2FollowS"))

  def oiFollowS: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("oiFollowS"))

  def aoFollowS: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("aoFollowS"))

  def p1Parked: F[Option[Boolean]] = safeAttributeSInt(tcsState.getIntegerAttribute("p1Parked"))
    .map(_.map(_ =!= 0))

  def p2Parked: F[Option[Boolean]] = safeAttributeSInt(tcsState.getIntegerAttribute("p2Parked"))
    .map(_.map(_ =!= 0))

  def oiParked: F[Option[Boolean]] = safeAttributeSInt(tcsState.getIntegerAttribute("oiParked"))
    .map(_.map(_ =!= 0))

  private val pwfs1OnAttr: CaAttribute[BinaryYesNo] = tcsState.addEnum("pwfs1On",
    s"${TcsTop}drives:p1Integrating", classOf[BinaryYesNo], "P1 integrating")
  def pwfs1On: F[Option[BinaryYesNo]] = safeAttribute(pwfs1OnAttr)

  private val pwfs2OnAttr: CaAttribute[BinaryYesNo] = tcsState.addEnum("pwfs2On",
    s"${TcsTop}drives:p2Integrating", classOf[BinaryYesNo], "P2 integrating")

  def pwfs2On:F[Option[BinaryYesNo]] = safeAttribute(pwfs2OnAttr)

  private val oiwfsOnAttr: CaAttribute[BinaryYesNo] = tcsState.addEnum("oiwfsOn",
    s"${TcsTop}drives:oiIntegrating", classOf[BinaryYesNo], "P2 integrating")

  def oiwfsOn: F[Option[BinaryYesNo]] = safeAttribute(oiwfsOnAttr)

  def sfName: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("sfName"))

  def sfParked: F[Option[Int]] = safeAttributeSInt(tcsState.getIntegerAttribute("sfParked"))

  def agHwName: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("agHwName"))

  def agHwParked: F[Option[Int]] = safeAttributeSInt(tcsState.getIntegerAttribute("agHwParked"))

  def instrAA: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("instrAA"))

  private val inPositionAttr: CaAttribute[String] = tcsState.getStringAttribute("inPosition")

  def inPosition:F[Option[String]] = safeAttribute(inPositionAttr)

  private val agInPositionAttr: CaAttribute[java.lang.Double] = tcsState.getDoubleAttribute("agInPosition")
  def agInPosition:F[Option[Double]] = safeAttributeSDouble(agInPositionAttr)

  val pwfs1ProbeGuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("p1", tcsState)

  val pwfs2ProbeGuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("p2", tcsState)

  val oiwfsProbeGuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("oi", tcsState)

  private val tcsStabilizeTime = 1.seconds

  private val filteredInPositionAttr: CaWindowStabilizer[String] = new CaWindowStabilizer[String](inPositionAttr, java.time.Duration.ofMillis(tcsStabilizeTime.toMillis))
  def filteredInPosition:F[Option[String]] = safeAttribute(filteredInPositionAttr)

  // This functions returns a SeqAction that, when run, will wait up to `timeout`
  // seconds for the TCS in-position flag to set to TRUE
  def waitInPosition(timeout: Time): F[Unit] = Sync[F].delay(filteredInPositionAttr.reset)
    .flatMap(waitForValueF(_, "TRUE", timeout,"TCS inposition flag"))

  private val agStabilizeTime = 1.seconds

  private val filteredAGInPositionAttr: CaWindowStabilizer[java.lang.Double] = new CaWindowStabilizer[java.lang.Double](agInPositionAttr, java.time.Duration.ofMillis(agStabilizeTime.toMillis))
  def filteredAGInPosition: F[Option[Double]] = safeAttributeSDouble(filteredAGInPositionAttr)

  // `waitAGInPosition` works like `waitInPosition`, but for the AG in-position flag.
  /* TODO: AG inposition can take up to 1[s] to react to a TCS command. If the value is read before that, it may induce
   * an error. A better solution is to detect the edge, from not in position to in-position.
   */
  private val AGSettleTime = 1100.milliseconds
  def waitAGInPosition(timeout: Time): F[Unit] = Sync[F].delay(Thread.sleep(AGSettleTime.toMilliseconds.toLong)) *>
    Sync[F].delay(filteredAGInPositionAttr.reset).flatMap(
      waitForValueF[java.lang.Double, F](_, 1.0, timeout, "AG inposition flag"))

  def hourAngle: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("ha"))

  def localTime: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("lt"))

  def trackingFrame: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("trkframe"))

  def trackingEpoch: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("trkepoch"))

  def equinox: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sourceAEquinox"))

  def trackingEquinox: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("sourceATrackEq"))

  def trackingDec: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("dectrack"))

  def trackingRA: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("ratrack"))

  def elevation: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("elevatio"))

  def azimuth: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("azimuth"))

  def crPositionAngle: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("crpa"))

  def ut: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("ut"))

  def date: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("date"))

  def m2Baffle: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2baffle"))

  def m2CentralBaffle: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("m2cenbaff"))

  def st: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("st"))

  def sfRotation: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sfrt2"))

  def sfTilt: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sftilt"))

  def sfLinear: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("sflinear"))

  def instrPA: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("instrPA"))

  def targetA: F[Option[List[Double]]] = safeAttributeSListSDouble(tcsState.getDoubleAttribute("targetA"))

  def aoFoldPosition: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("aoName"))

  private val useAoAttr: CaAttribute[BinaryYesNo] = tcsState.addEnum("useAo",
    s"${TcsTop}im:AOConfigFlag.VAL", classOf[BinaryYesNo], "Using AO flag")
  def useAo: F[Option[BinaryYesNo]] = safeAttribute(useAoAttr)

  def airmass: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("airmass"))

  def airmassStart: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("amstart"))

  def airmassEnd: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("amend"))

  def carouselMode: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("cguidmod"))

  def crFollow: F[Option[Int]]  = safeAttributeSInt(tcsState.getIntegerAttribute("crfollow"))

  def sourceATarget: Target[F] = new Target[F] {
    override def epoch: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("sourceAEpoch"))

    override def equinox: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("sourceAEquinox"))

    override def radialVelocity: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("radvel"))

    override def frame: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("frame"))

    override def centralWavelenght: F[Option[Double]] = sourceAWavelength

    override def ra: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("ra"))

    override def objectName: F[Option[String]] = safeAttribute(tcsState.getStringAttribute("sourceAObjectName"))

    override def dec: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("dec"))

    override def parallax: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("parallax"))

    override def properMotionRA: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("pmra"))

    override def properMotionDec: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("pmdec"))
  }

  private def target(base: String): Target[F] = new Target[F] {
      override def epoch: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(base + "aepoch"))
      override def equinox: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(base + "aequin"))
      override def radialVelocity:F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute(base + "arv"))
      override def frame: F[Option[String]]  = safeAttribute(tcsState.getStringAttribute(base + "aframe"))
      override def centralWavelenght:F[Option[Double]] =
        safeAttributeSDouble(tcsState.getDoubleAttribute(base + "awavel"))
      override def ra:F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute(base + "ara"))
      override def objectName: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(base + "aobjec"))
      override def dec:F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute(base + "adec"))
      override def parallax:F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute(base + "aparal"))
      override def properMotionRA:F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute(base + "apmra"))
      override def properMotionDec:F[Option[Double]] =
        safeAttributeSDouble(tcsState.getDoubleAttribute(base + "apmdec"))
    }

  val pwfs1Target: Target[F] = target("p1")

  val pwfs2Target: Target[F] = target("p2")

  val oiwfsTarget: Target[F] = target("oi")

  def parallacticAngle: F[Option[Angle]] =
    safeAttributeSDouble(tcsState.getDoubleAttribute("parangle")).map(_.map(Degrees(_)))

  def m2UserFocusOffset: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("m2ZUserOffset"))

  private val pwfs1Status = epicsService.getStatusAcceptor("pwfs1state")

  def pwfs1IntegrationTime: F[Option[Double]] = safeAttributeSDouble(pwfs1Status.getDoubleAttribute("intTime"))

  private val pwfs2Status = epicsService.getStatusAcceptor("pwfs2state")

  def pwfs2IntegrationTime: F[Option[Double]] = safeAttributeSDouble(pwfs2Status.getDoubleAttribute("intTime"))

  private val oiwfsStatus = epicsService.getStatusAcceptor("oiwfsstate")

  // Attribute must be changed back to Double after EPICS channel is fixed.
  def oiwfsIntegrationTime:F[Option[Double]]  = safeAttributeSDouble(oiwfsStatus.getDoubleAttribute("intTime"))


  private def instPort(name: String): F[Option[Int]] =
    safeAttributeSInt(tcsState.getIntegerAttribute(s"${name}Port"))

  def gsaoiPort: F[Option[Int]] = instPort("gsaoi")
  def gpiPort: F[Option[Int]]= instPort("gpi")
  def f2Port: F[Option[Int]] = instPort("f2")
  def niriPort: F[Option[Int]] = instPort("niri")
  def gnirsPort: F[Option[Int]] = instPort("nirs")
  def nifsPort: F[Option[Int]] = instPort("nifs")
  def gmosPort: F[Option[Int]] = instPort("gmos")
  def ghostPort: F[Option[Int]] = instPort("ghost")

  def aoGuideStarX: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("aogsx"))

  def aoGuideStarY: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("aogsy"))

  def aoPreparedCMX: F[Option[Double]] = safeAttribute(tcsState.getStringAttribute("cmprepx"))
    .map(_.flatMap(v => Try(v.toDouble).toOption))

  def aoPreparedCMY: F[Option[Double]] = safeAttribute(tcsState.getStringAttribute("cmprepy"))
    .map(_.flatMap(v => Try(v.toDouble).toOption))

  // GeMS Commands
  object wavelG1 extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelG1"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object wavelG2 extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelG2"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object wavelG3 extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelG3"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  object wavelG4 extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender("wavelG4"))

    private val wavel = cs.map(_.getDouble("wavel"))

    def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  def gwfs1Target: Target[F] = target("g1")

  def gwfs2Target: Target[F] = target("g2")

  def gwfs3Target: Target[F] = target("g3")

  def gwfs4Target: Target[F] = target("g4")

  val ngs1ProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("ngsPr1Follow", epicsService)

  val ngs2ProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("ngsPr2Follow", epicsService)

  val ngs3ProbeFollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("ngsPr3Follow", epicsService)

  val odgw1FollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("odgw1Follow", epicsService)

  val odgw2FollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("odgw2Follow", epicsService)

  val odgw3FollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("odgw3Follow", epicsService)

  val odgw4FollowCmd: ProbeFollowCmd[F] = new ProbeFollowCmd("odgw4Follow", epicsService)

  // GeMS statuses

  val ngs1FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("ngs1Follow",
    s"${TcsTop}ngsPr1FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def ngs1Follow: F[Option[Boolean]] =
    Nested(safeAttribute(ngs1FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val ngs2FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("ngs2Follow",
    s"${TcsTop}ngsPr2FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def ngs2Follow: F[Option[Boolean]] =
    Nested(safeAttribute(ngs2FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val ngs3FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("ngs3Follow",
    s"${TcsTop}ngsPr3FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def ngs3Follow: F[Option[Boolean]] =
    Nested(safeAttribute(ngs3FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val odgw1FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("odgw1Follow",
    s"${TcsTop}odgw1FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def odgw1Follow: F[Option[Boolean]] =
    Nested(safeAttribute(odgw1FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val odgw2FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("odgw2Follow",
    s"${TcsTop}odgw2FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def odgw2Follow: F[Option[Boolean]] =
    Nested(safeAttribute(odgw2FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val odgw3FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("odgw3Follow",
    s"${TcsTop}odgw3FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def odgw3Follow: F[Option[Boolean]] =
    Nested(safeAttribute(odgw3FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val odgw4FollowAttr: CaAttribute[BinaryEnabledDisabled] = tcsState.addEnum("odgw4Follow",
    s"${TcsTop}odgw4FollowStat.VAL", classOf[BinaryEnabledDisabled])
  def odgw4Follow: F[Option[Boolean]] =
    Nested(safeAttribute(odgw4FollowAttr)).map(_ === BinaryEnabledDisabled.Enabled).value

  val OdgwParkedState: String = "Parked"

  def odgw1Parked: F[Option[Boolean]] =
    Nested(safeAttribute(tcsState.getStringAttribute("odgw1Parked"))).map(_ === OdgwParkedState).value

  def odgw2Parked: F[Option[Boolean]] =
    Nested(safeAttribute(tcsState.getStringAttribute("odgw2Parked"))).map(_ === OdgwParkedState).value

  def odgw3Parked: F[Option[Boolean]] =
    Nested(safeAttribute(tcsState.getStringAttribute("odgw3Parked"))).map(_ === OdgwParkedState).value

  def odgw4Parked: F[Option[Boolean]] =
    Nested(safeAttribute(tcsState.getStringAttribute("odgw4Parked"))).map(_ === OdgwParkedState).value

  def g1MapName: F[Option[GemsSource]] =
    safeAttribute(tcsState.getStringAttribute("g1MapName"))
      .map(_.flatMap{x:String => GemsSource.all.find(_.epicsVal === x)})

  def g2MapName: F[Option[GemsSource]] =
    safeAttribute(tcsState.getStringAttribute("g2MapName"))
      .map(_.flatMap{x:String => GemsSource.all.find(_.epicsVal === x)})

  def g3MapName: F[Option[GemsSource]] =
    safeAttribute(tcsState.getStringAttribute("g3MapName"))
      .map(_.flatMap{x:String => GemsSource.all.find(_.epicsVal === x)})

  def g4MapName: F[Option[GemsSource]] =
    safeAttribute(tcsState.getStringAttribute("g4MapName"))
      .map(_.flatMap{x:String => GemsSource.all.find(_.epicsVal === x)})

  def g1Wavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("g1Wavelength"))

  def g2Wavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("g2Wavelength"))

  def g3Wavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("g3Wavelength"))

  def g4Wavelength: F[Option[Double]] = safeAttributeSDouble(tcsState.getDoubleAttribute("g4Wavelength"))

  val g1GuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("g1", tcsState)

  val g2GuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("g2", tcsState)

  val g3GuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("g3", tcsState)

  val g4GuideConfig: ProbeGuideConfig[F] = new ProbeGuideConfig("g4", tcsState)

}

object TcsEpics extends EpicsSystem[TcsEpics[IO]] {

  override val className: String = getClass.getName
  override val Log: Logger = getLogger
  override val CA_CONFIG_FILE: String = "/Tcs.xml"

  override def build(service: CaService, tops: Map[String, String]) = new TcsEpics[IO](service, tops)

  sealed class ProbeGuideCmd[F[_]: Async](csName: String, epicsService: CaService) extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val nodachopa = cs.map(_.getString("nodachopa"))
    def setNodachopa(v: String): F[Unit] = setParameter(nodachopa, v)

    private val nodachopb = cs.map(_.getString("nodachopb"))
    def setNodachopb(v: String): F[Unit] = setParameter(nodachopb, v)

    private val nodachopc = cs.map(_.getString("nodachopc"))
    def setNodachopc(v: String): F[Unit] = setParameter(nodachopc, v)

    private val nodbchopa = cs.map(_.getString("nodbchopa"))
    def setNodbchopa(v: String): F[Unit] = setParameter(nodbchopa, v)

    private val nodbchopb = cs.map(_.getString("nodbchopb"))
    def setNodbchopb(v: String): F[Unit] = setParameter(nodbchopb, v)

    private val nodbchopc = cs.map(_.getString("nodbchopc"))
    def setNodbchopc(v: String): F[Unit] = setParameter(nodbchopc, v)

    private val nodcchopa = cs.map(_.getString("nodcchopa"))
    def setNodcchopa(v: String): F[Unit] = setParameter(nodcchopa, v)

    private val nodcchopb = cs.map(_.getString("nodcchopb"))
    def setNodcchopb(v: String): F[Unit] = setParameter(nodcchopb, v)

    private val nodcchopc = cs.map(_.getString("nodcchopc"))
    def setNodcchopc(v: String): F[Unit] = setParameter(nodcchopc, v)
  }

  sealed class WfsObserveCmd[F[_]: Async](csName: String, epicsService: CaService) extends EpicsCommand {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val noexp = cs.map(_.getInteger("noexp"))
    def setNoexp(v: Integer): F[Unit] = setParameter(noexp, v)

    private val int = cs.map(_.getDouble("int"))
    def setInt(v: Double): F[Unit] = setParameter[F, java.lang.Double](int, v)

    private val outopt = cs.map(_.getString("outopt"))
    def setOutopt(v: String): F[Unit] = setParameter(outopt, v)

    private val label = cs.map(_.getString("label"))
    def setLabel(v: String): F[Unit] = setParameter(label, v)

    private val output = cs.map(_.getString("output"))
    def setOutput(v: String): F[Unit] = setParameter(output, v)

    private val path = cs.map(_.getString("path"))
    def setPath(v: String): F[Unit] = setParameter(path, v)

    private val name = cs.map(_.getString("name"))
    def setName(v: String): F[Unit] = setParameter(name, v)
  }

  final class ProbeFollowCmd[F[_]: Async](csName: String, epicsService: CaService) extends EpicsCommand {
    override protected val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val follow = cs.map(_.getString("followState"))
    def setFollowState(v: String): F[Unit] = setParameter(follow, v)
  }

  class ProbeGuideConfig[F[_]: Sync](protected val prefix: String, protected val tcsState: CaStatusAcceptor) {
    def nodachopa: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodachopa"))
    def nodachopb: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodachopb"))
    def nodachopc: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodachopc"))
    def nodbchopa: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodbchopa"))
    def nodbchopb: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodbchopb"))
    def nodbchopc: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodbchopc"))
    def nodcchopa: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodcchopa"))
    def nodcchopb: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodcchopb"))
    def nodcchopc: F[Option[String]] = safeAttribute(tcsState.getStringAttribute(prefix + "nodcchopc"))
  }

  sealed trait Target[F[_]] {
    def objectName: F[Option[String]]
    def ra: F[Option[Double]]
    def dec: F[Option[Double]]
    def frame: F[Option[String]]
    def equinox: F[Option[String]]
    def epoch: F[Option[String]]
    def properMotionRA: F[Option[Double]]
    def properMotionDec: F[Option[Double]]
    def centralWavelenght: F[Option[Double]]
    def parallax: F[Option[Double]]
    def radialVelocity: F[Option[Double]]
  }

  // TODO: Delete me after fully moved to tagless
  implicit class TargetIOOps(val tio: Target[IO]) extends AnyVal{
    def to[F[_]: LiftIO]: Target[F] = new Target[F] {
      def objectName: F[Option[String]] = tio.objectName.to[F]
      def ra: F[Option[Double]] = tio.ra.to[F]
      def dec: F[Option[Double]] = tio.dec.to[F]
      def frame: F[Option[String]] = tio.frame.to[F]
      def equinox: F[Option[String]] = tio.equinox.to[F]
      def epoch: F[Option[String]] = tio.epoch.to[F]
      def properMotionRA: F[Option[Double]] = tio.properMotionRA.to[F]
      def properMotionDec: F[Option[Double]] = tio.properMotionDec.to[F]
      def centralWavelenght: F[Option[Double]] = tio.centralWavelenght.to[F]
      def parallax: F[Option[Double]] = tio.parallax.to[F]
      def radialVelocity: F[Option[Double]] = tio.radialVelocity.to[F]
    }
  }

}
