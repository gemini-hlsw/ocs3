// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.engine

import seqexec.engine.Result._
import seqexec.model.{ClientId, StepId}
import gem.Observation
import seqexec.model.enum.Resource

/**
  * Events generated internally by the Engine.
  */
sealed trait SystemEvent
final case class Completed[R<:RetVal](id: Observation.Id, stepId: StepId, i: Int, r: OK[R])
  extends SystemEvent
final case class PartialResult[R<:PartialVal](sid: Observation.Id, stepId: StepId, i: Int,
                                              r: Partial[R]) extends SystemEvent
final case class Paused[C <: PauseContext](id: Observation.Id, i: Int, r: Result.Paused[C])
  extends SystemEvent
final case class Failed(id: Observation.Id, i: Int, e: Result.Error) extends SystemEvent
final case class Busy(id: Observation.Id, clientId: ClientId) extends SystemEvent
final case class BreakpointReached(id: Observation.Id) extends SystemEvent
final case class Executed(id: Observation.Id) extends SystemEvent
final case class Executing(id: Observation.Id) extends SystemEvent
final case class Finished(id: Observation.Id) extends SystemEvent
object Null extends SystemEvent

// Single action commands
final case class SingleRunCompleted[R<:RetVal](actionCoords: ActionCoords, res: Resource, r: OK[R])
  extends SystemEvent
final case class SingleRunFailed(actionCoords: ActionCoords, res: Resource, e: Result.Error)
  extends SystemEvent