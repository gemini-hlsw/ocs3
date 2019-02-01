// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model.enum

import cats.Eq
import cats.implicits._
import gem.Observation
import seqexec.model.StepId

sealed trait SingleActionOp extends Product with Serializable {
  val sid: Observation.Id
  val stepId: StepId
  val resource: Resource
}

object SingleActionOp {
  final case class Started(sid: Observation.Id, stepId: StepId, resource: Resource)
    extends SingleActionOp
  final case class Completed(sid: Observation.Id, stepId: StepId, resource: Resource)
    extends SingleActionOp
  final case class Error(sid: Observation.Id, stepId: StepId, resource: Resource)
    extends SingleActionOp

  implicit val equal: Eq[SingleActionOp] = Eq.instance {
    case (Started(a, c, e), Started(b, d, f))     => a === b && c === d && e === f
    case (Completed(a, c, e), Completed(b, d, f)) => a === b && c === d && e === f
    case (Error(a, c, e), Error(b, d, f))         => a === b && c === d && e === f
    case _                                  => false
  }
}