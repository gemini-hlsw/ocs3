// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model.enum

import cats.Show
import gem.util.Enumerated

sealed abstract class CloudCover(val toInt: Int, val label: String)
  extends Product with Serializable

object CloudCover {

  case object Percent50 extends CloudCover(50,  "50%/Clear")
  case object Percent70 extends CloudCover(70,  "70%/Cirrus")
  case object Percent80 extends CloudCover(80,  "80%/Cloudy")
  case object Any       extends CloudCover(100, "Any")

  val all: List[CloudCover] =
    List(Percent50, Percent70, Percent80, Any)

  implicit val showCloudCover: Show[CloudCover] =
    Show.show(_.label)

  /** @group Typeclass Instances */
  implicit val CloudCoverEnumerated: Enumerated[CloudCover] =
    new Enumerated[CloudCover] {
      def all = CloudCover.all
      def tag(a: CloudCover): String = a.label
    }
}
