// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.tcs

import cats._
import cats.data.{NonEmptyList, OneAnd}
import cats.implicits._
import seqexec.server.SeqAction
import edu.gemini.spModel.core.Wavelength
import gem.enum.LightSinkName
import squants.{Angle, Length}

/**
 * Created by jluhrs on 7/30/15.
 *
 * Interface to change and retrieve the TCS state.
 * Most of the code deals with representing the state of the TCS subsystems.
 */

trait TcsController {
  import TcsController._

  def getConfig: SeqAction[TcsConfig]

  def guide(gc: GuideConfig): SeqAction[Unit]

  def applyConfig(subsystems: NonEmptyList[Subsystem], tc: TcsConfig): SeqAction[Unit]

  def notifyObserveStart: SeqAction[Unit]

  def notifyObserveEnd: SeqAction[Unit]
}

// scalastyle:off
object TcsController {

  final case class Requested[T](self: T) extends AnyVal

  /** Enumerated type for Tip/Tilt Source. */
  sealed trait TipTiltSource
  object TipTiltSource {
    case object PWFS1 extends TipTiltSource
    case object PWFS2 extends TipTiltSource
    case object OIWFS extends TipTiltSource
    case object GAOS  extends TipTiltSource
  }

  /** Enumerated type for M1 Source. */
  sealed trait M1Source
  object M1Source {
    case object PWFS1 extends M1Source
    case object PWFS2 extends M1Source
    case object OIWFS extends M1Source
    case object GAOS  extends M1Source
    case object HRWFS extends M1Source
  }

  /** Enumerated type for Coma option. */
  sealed trait ComaOption
  object ComaOption {
    case object ComaOn  extends ComaOption
    case object ComaOff extends ComaOption
  }

  /** Data type for M2 guide config. */
  sealed trait M2GuideConfig
  object M2GuideConfig {
    implicit val show: Show[M2GuideConfig] = Show.fromToString
  }
  case object M2GuideOff extends M2GuideConfig
  final case class M2GuideOn(coma: ComaOption, source: Set[TipTiltSource]) extends M2GuideConfig {
    def setComa(v: ComaOption): M2GuideConfig = M2GuideOn(v, source)
    def setSource(v: Set[TipTiltSource]): M2GuideConfig = M2GuideOn(coma, v)
  }

  /** Data type for M2 guide config. */
  sealed trait M1GuideConfig
  object M1GuideConfig {
    implicit val show: Show[M1GuideConfig] = Show.fromToString
  }
  case object M1GuideOff extends M1GuideConfig
  final case class M1GuideOn(source: M1Source) extends M1GuideConfig

  /** Enumerated type for beams A, B, and C. */
  sealed trait Beam extends Product with Serializable
  object Beam {
    case object A extends Beam
    case object B extends Beam
    case object C extends Beam

    implicit val equal: Eq[Beam] = Eq.fromUniversalEquals
  }

  /**
   * Data type for combined configuration of nod position (telescope orientation) and chop position
   * (M2 orientation)
   */
  final case class NodChop(nod: Beam, chop: Beam)
  object NodChop {
    implicit def EqNodChop: Eq[NodChop] =
      Eq[(Beam, Beam)].contramap(x => (x.nod, x.chop))
  }

  /** Enumerated type for nod/chop tracking. */
  sealed trait NodChopTrackingOption
  object NodChopTrackingOption {

    case object NodChopTrackingOn  extends NodChopTrackingOption
    case object NodChopTrackingOff extends NodChopTrackingOption

    def fromBoolean(on: Boolean): NodChopTrackingOption =
      if (on) NodChopTrackingOn else NodChopTrackingOff

  }
  import NodChopTrackingOption._ // needed below

  // TCS can be configured to update a guide probe position only for certain nod-chop positions.
  sealed trait NodChopTrackingConfig {
    def get(nodchop: NodChop): NodChopTrackingOption
  }
  sealed trait ActiveNodChopTracking extends NodChopTrackingConfig

    // If x is of type ActiveNodChopTracking then ∃ a:NodChop ∍ x.get(a) == NodChopTrackingOn
    // How could I reflect that in the code?

  object NodChopTrackingConfig {

    object None extends NodChopTrackingConfig {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOff
    }

    object Normal extends ActiveNodChopTracking {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOption.fromBoolean(nodchop.nod === nodchop.chop)
    }

    final case class Special(s: OneAnd[List, NodChop]) extends ActiveNodChopTracking {
      def get(nodchop: NodChop): NodChopTrackingOption =
        NodChopTrackingOption.fromBoolean(s.exists(_ === nodchop))
    }

  }

  // A probe tracking configuration is specified by its nod-chop tracking table
  // and its follow flag. The first tells TCS when to update the target track
  // followed by the probe, and the second tells the probe if it must follow
  // the target track.

  /** Enumerated type for follow on/off. */
  sealed trait FollowOption
  object FollowOption {
    case object FollowOff extends FollowOption
    case object FollowOn  extends FollowOption
  }
  import FollowOption._

  /** Data type for probe tracking config. */
  sealed abstract class ProbeTrackingConfig(
    val follow: FollowOption,
    val getNodChop: NodChopTrackingConfig
  )
  object ProbeTrackingConfig {
    case object Parked extends ProbeTrackingConfig(FollowOff, NodChopTrackingConfig.None)
    case object Off extends ProbeTrackingConfig(FollowOff, NodChopTrackingConfig.None)
    final case class On(ndconfig: ActiveNodChopTracking) extends ProbeTrackingConfig(FollowOn, ndconfig)
  }

  /** Enumerated type for HRWFS pickup position. */
  sealed trait HrwfsPickupPosition
  object HrwfsPickupPosition {
    case object IN     extends HrwfsPickupPosition
    case object OUT    extends HrwfsPickupPosition
    case object Parked extends HrwfsPickupPosition
    implicit val show: Show[HrwfsPickupPosition] = Show.fromToString
  }

  sealed trait HrwfsConfig
  object HrwfsConfig {
    case object Auto                                  extends HrwfsConfig
    final case class Manual(pos: HrwfsPickupPosition) extends HrwfsConfig
    implicit val show: Show[HrwfsConfig] = Show.fromToString
  }

  /** Enumerated type for light source. */
  sealed trait LightSource
  object LightSource {
    case object Sky  extends LightSource
    case object AO   extends LightSource
    case object GCAL extends LightSource
  }

  /** Data type for science fold position. */
  sealed trait ScienceFoldPosition
  object ScienceFoldPosition {
    case object Parked extends ScienceFoldPosition
    final case class Position(source: LightSource, sink: LightSinkName) extends ScienceFoldPosition
    implicit val show: Show[ScienceFoldPosition] = Show.fromToString
  }

  /** Enumerated type for offloading of tip/tilt corrections from M2 to mount. */
  sealed trait MountGuideOption
  object MountGuideOption {
    case object MountGuideOff extends MountGuideOption
    case object MountGuideOn  extends MountGuideOption
  }

  /** Data type for guide config. */
  final case class GuideConfig(mountGuide: MountGuideOption, m1Guide: M1GuideConfig, m2Guide: M2GuideConfig) {
    def setMountGuide(v: MountGuideOption): GuideConfig = GuideConfig(v, m1Guide, m2Guide)
    def setM1Guide(v: M1GuideConfig): GuideConfig = GuideConfig(mountGuide, v, m2Guide)
    def setM2Guide(v: M2GuideConfig): GuideConfig = GuideConfig(mountGuide, m1Guide, v)
  }

  // TCS expects offsets as two length quantities (in millimeters) in the focal plane
  final case class OffsetX(self: Length)
  object OffsetX {
    implicit val EqOffsetX: Eq[OffsetX] =
      Eq[Double].contramap(_.self.value)
  }

  final case class OffsetY(self: Length)
  object OffsetY {
    implicit val EqOffsetY: Eq[OffsetY] =
      Eq[Double].contramap(_.self.value)
  }

  final case class FocalPlaneOffset(x: OffsetX, y: OffsetY)
  object FocalPlaneOffset {
    implicit val EqFocalPlaneOffset: Eq[FocalPlaneOffset] =
      Eq.by(o => (o.x, o.y))
  }

  final case class OffsetA(self: FocalPlaneOffset)
  object OffsetA {
    implicit val EqOffsetA: Eq[OffsetA] =
      Eq.by(_.self)
  }

  final case class OffsetB(self: FocalPlaneOffset)
  object OffsetB {
    implicit val EqOffsetB: Eq[OffsetB] =
      Eq.by(_.self)
  }

  final case class OffsetC(self: FocalPlaneOffset)
  object OffsetC {
    implicit val EqOffsetC: Eq[OffsetC] =
      Eq.by(_.self)
  }

  // The WavelengthX classes cannot be value classes, because Wavelength is now a value class, and they cannot be
  // nested.
  final case class WavelengthA(self: Wavelength)
  final case class WavelengthB(self: Wavelength)
  final case class WavelengthC(self: Wavelength)

  final case class TelescopeConfig(
    offsetA: OffsetA, offsetB: OffsetB, offsetC: OffsetC,
    wavelA:  WavelengthA, wavelB: WavelengthB, wavelC: WavelengthC,
    m2beam: Beam
  ) {

    // TODO: these in terms of .copy
    def setOffsetA(v: FocalPlaneOffset): TelescopeConfig = TelescopeConfig(OffsetA(v), offsetB, offsetC, wavelA, wavelB, wavelC, m2beam)
    def setOffsetB(v: FocalPlaneOffset): TelescopeConfig = TelescopeConfig(offsetA, OffsetB(v), offsetC, wavelA, wavelB, wavelC, m2beam)
    def setOffsetC(v: FocalPlaneOffset): TelescopeConfig = TelescopeConfig(offsetA, offsetB, OffsetC(v), wavelA, wavelB, wavelC, m2beam)
    def setWavelengthA(v: Wavelength): TelescopeConfig = TelescopeConfig(offsetA, offsetB, offsetC, WavelengthA(v), wavelB, wavelC, m2beam)
    def setWavelengthB(v: Wavelength): TelescopeConfig = TelescopeConfig(offsetA, offsetB, offsetC, wavelA, WavelengthB(v), wavelC, m2beam)
    def setWavelengthC(v: Wavelength): TelescopeConfig = TelescopeConfig(offsetA, offsetB, offsetC, wavelA, wavelB, WavelengthC(v), m2beam)
    def setBeam(v: Beam): TelescopeConfig = TelescopeConfig(offsetA, offsetB, offsetC, wavelA, wavelB, wavelC, v)
  }
  object TelescopeConfig {
    implicit val show: Show[TelescopeConfig] = Show.fromToString
  }

  final case class ProbeTrackingConfigP1(self: ProbeTrackingConfig) extends AnyVal
  object ProbeTrackingConfigP1 {
    implicit val show: Show[ProbeTrackingConfigP1] = Show.fromToString
  }
  final case class ProbeTrackingConfigP2(self: ProbeTrackingConfig) extends AnyVal
  object ProbeTrackingConfigP2 {
    implicit val show: Show[ProbeTrackingConfigP2] = Show.fromToString
  }
  final case class ProbeTrackingConfigOI(self: ProbeTrackingConfig) extends AnyVal
  object ProbeTrackingConfigOI {
    implicit val show: Show[ProbeTrackingConfigOI] = Show.fromToString
  }
  final case class ProbeTrackingConfigAO(self: ProbeTrackingConfig) extends AnyVal
  object ProbeTrackingConfigAO {
    implicit val show: Show[ProbeTrackingConfigAO] = Show.fromToString
  }

  final case class GuidersTrackingConfig(
    pwfs1: ProbeTrackingConfigP1,
    pwfs2: ProbeTrackingConfigP2,
    oiwfs: ProbeTrackingConfigOI,
    aowfs: ProbeTrackingConfigAO
  ) {
    def setPwfs1TrackingConfig(v: ProbeTrackingConfig): GuidersTrackingConfig = GuidersTrackingConfig(ProbeTrackingConfigP1(v), pwfs2, oiwfs, aowfs)
    def setPwfs2TrackingConfig(v: ProbeTrackingConfig): GuidersTrackingConfig = GuidersTrackingConfig(pwfs1, ProbeTrackingConfigP2(v), oiwfs, aowfs)
    def setOiwfsTrackingConfig(v: ProbeTrackingConfig): GuidersTrackingConfig = GuidersTrackingConfig(pwfs1, pwfs2, ProbeTrackingConfigOI(v), aowfs)
    def setAowfsTrackingConfig(v: ProbeTrackingConfig): GuidersTrackingConfig = GuidersTrackingConfig(pwfs1, pwfs2, oiwfs, ProbeTrackingConfigAO(v))
  }

  sealed trait GuiderSensorOption
  object GuiderSensorOff extends GuiderSensorOption
  object GuiderSensorOn extends GuiderSensorOption

  final case class GuiderSensorOptionP1(self: GuiderSensorOption) extends AnyVal
  object GuiderSensorOptionP1 {
    implicit val show: Show[GuiderSensorOptionP1] = Show.fromToString
  }
  final case class GuiderSensorOptionP2(self: GuiderSensorOption) extends AnyVal
  object GuiderSensorOptionP2 {
    implicit val show: Show[GuiderSensorOptionP2] = Show.fromToString
  }
  final case class GuiderSensorOptionOI(self: GuiderSensorOption) extends AnyVal
  object GuiderSensorOptionOI {
    implicit val show: Show[GuiderSensorOptionOI] = Show.fromToString
  }
  final case class GuiderSensorOptionAO(self: GuiderSensorOption) extends AnyVal
  object GuiderSensorOptionAO {
    implicit val show: Show[GuiderSensorOptionAO] = Show.fromToString
  }

  // A enabled guider means it is taking images and producing optical error measurements.
  final case class GuidersEnabled(
    pwfs1: GuiderSensorOptionP1,
    pwfs2: GuiderSensorOptionP2,
    oiwfs: GuiderSensorOptionOI
  ) {
    def setPwfs1GuiderSensorOption(v: GuiderSensorOption): GuidersEnabled = this.copy(pwfs1 = GuiderSensorOptionP1(v))
    def setPwfs2GuiderSensorOption(v: GuiderSensorOption): GuidersEnabled = this.copy(pwfs2 = GuiderSensorOptionP2(v))
    def setOiwfsGuiderSensorOption(v: GuiderSensorOption): GuidersEnabled = this.copy(oiwfs = GuiderSensorOptionOI(v))
  }

  final case class AGConfig(sfPos: Option[ScienceFoldPosition], hrwfs: Option[HrwfsConfig])

  final case class InstrumentAlignAngle(self: Angle) extends AnyVal

  final case class TcsConfig(
    gc:  GuideConfig,
    tc:  TelescopeConfig,
    gtc: GuidersTrackingConfig,
    ge:  GuidersEnabled,
    agc: AGConfig,
    iaa: InstrumentAlignAngle
  ) {
    def setGuideConfig(v: GuideConfig): TcsConfig = this.copy(gc = v)
    def setTelescopeConfig(v: TelescopeConfig): TcsConfig = this.copy(tc = v)
    def setGuidersTrackingConfig(v: GuidersTrackingConfig): TcsConfig = this.copy(gtc = v)
    def setGuidersEnabled(v: GuidersEnabled): TcsConfig = this.copy(ge = v)
    def setAGConfig(v: AGConfig): TcsConfig = this.copy(agc = v)
    def setIAA(v: InstrumentAlignAngle): TcsConfig = this.copy(iaa = v)
  }

  sealed trait Subsystem extends Product with Serializable
  object Subsystem {
    // Instrument internal WFS
    case object OIWFS  extends Subsystem
    // Peripheral WFS 1
    case object P1WFS  extends Subsystem
    // Peripheral WFS 2
    case object P2WFS  extends Subsystem
    // Internal AG mechanisms (science fold, AC arm)
    case object AGUnit extends Subsystem
    // Mount and cass-rotator
    case object Mount  extends Subsystem
    // Primary mirror
    case object M1     extends Subsystem
    // Secondary mirror
    case object M2     extends Subsystem

    val all: NonEmptyList[Subsystem] = NonEmptyList.of(OIWFS, P1WFS, P2WFS, AGUnit, Mount, M1, M2)
    val allButOI: NonEmptyList[Subsystem] = NonEmptyList.of(P1WFS, P2WFS, AGUnit, Mount, M1, M2)

    implicit val show: Show[Subsystem] = Show.show { _.productPrefix }
    implicit val equal: Eq[Subsystem] = Eq.fromUniversalEquals
  }

}
// scalastyle:on
