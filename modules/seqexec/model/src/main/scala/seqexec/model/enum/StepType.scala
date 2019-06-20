// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model.enum

import cats.Show
import cats.implicits._
import gem.util.Enumerated

sealed abstract class StepType(val label: String)
  extends Product with Serializable

object StepType {

  case object Object        extends StepType("OBJECT")
  case object Arc           extends StepType("ARC")
  case object Flat          extends StepType("FLAT")
  case object Bias          extends StepType("BIAS")
  case object Dark          extends StepType("DARK")
  case object Calibration   extends StepType("CAL")
  case object AlignAndCalib extends StepType("A & C")

  implicit val show: Show[StepType] =
    Show.show(_.label)

  val all: List[StepType] =
    List(Object, Arc, Flat, Bias, Dark, Calibration, AlignAndCalib)

  def fromString(s: String): Option[StepType] =
    all.find(_.label === s)

  /** @group Typeclass Instances */
  implicit val StepTypeEnumerated: Enumerated[StepType] =
    new Enumerated[StepType] {
      def all = StepType.all
      def tag(a: StepType): String = a.label
    }

}
