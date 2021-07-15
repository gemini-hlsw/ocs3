// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import java.util.concurrent.TimeUnit.SECONDS

import scala.concurrent.duration.FiniteDuration

import cats.Eq
import cats.syntax.all._
import edu.gemini.seqexec.server.tcs.BinaryEnabledDisabled
import edu.gemini.seqexec.server.tcs.BinaryOnOff
import edu.gemini.seqexec.server.tcs.BinaryYesNo
import monocle.Iso
import shapeless.tag
import shapeless.tag.@@
import squants.Angle
import squants.Length
import squants.Ratio
import squants.space.Arcseconds
import squants.space.Millimeters

package tcs {

  final class FocalPlaneScale(angle: Angle, length: Length) extends Ratio[Angle, Length] {

    override def base: Angle = angle

    override def counter: Length = length

    def times(l: Length): Angle = convertToBase(l)
    def *(l:     Length): Angle = times(l)

    def divide(a: Angle): Length = convertToCounter(a)
  }

  object FocalPlaneScale {

    implicit class AngleOps(a: Angle) {
      def dividedBy(fps: FocalPlaneScale): Length = fps.divide(a)
      def /(fps:         FocalPlaneScale): Length = dividedBy(fps)
    }

    implicit class LengthOps(l: Length) {
      def times(fps: FocalPlaneScale): Angle = fps * l
      def *(fps:     FocalPlaneScale): Angle = times(fps)
    }

  }

  sealed case class WithDebug[A](self: A, debug: String) {
    def mapDebug(f: String => String): WithDebug[A] = this.copy(debug = f(debug))
  }

}

package object tcs {
  val BottomPort: Int  = 1
  val InvalidPort: Int = 0

  val tcsTimeout: FiniteDuration = FiniteDuration(90, SECONDS)
  val agTimeout: FiniteDuration  = FiniteDuration(90, SECONDS)

  val NonStopExposures = -1

  // Focal plane scale, expressed with squants quantities.
  val FOCAL_PLANE_SCALE = new FocalPlaneScale(Arcseconds(1.61144), Millimeters(1))

  val pwfs1OffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE
  val pwfs2OffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE

  val AoOffsetThreshold: Length = Arcseconds(0.01) / FOCAL_PLANE_SCALE

  implicit val ooEq: Eq[BinaryOnOff]              =
    Eq[Int].contramap(_.ordinal())
  implicit val ynEq: Eq[BinaryYesNo]              =
    Eq[Int].contramap(_.ordinal())
  implicit val endisEq: Eq[BinaryEnabledDisabled] =
    Eq[Int].contramap(_.ordinal())

  def tagIso[B, T]: Iso[B @@ T, B] = Iso.apply[B @@ T, B](x => x)(tag[T](_))

  implicit class WithDebugOps[A](v: A) {
    def withDebug(msg: String): WithDebug[A] = WithDebug(v, msg)
  }

}
