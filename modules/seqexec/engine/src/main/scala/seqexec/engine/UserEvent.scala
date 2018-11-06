// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.engine

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import gem.Observation
import seqexec.model.ClientId
import seqexec.model.UserDetails

/**
  * Events generated by the user.
  */
sealed trait UserEvent[D<:Engine.Types] {
  def user: Option[UserDetails]
  def username: String = user.foldMap(_.username)
}

final case class Start[D<:Engine.Types](id: Observation.Id, user: Option[UserDetails], clientId: ClientId, userCheck: D#StateType => Boolean) extends UserEvent[D]
final case class Pause[D<:Engine.Types](id: Observation.Id, user: Option[UserDetails]) extends UserEvent[D]
final case class CancelPause[D<:Engine.Types](id: Observation.Id, user: Option[UserDetails]) extends UserEvent[D]
final case class Breakpoint[D<:Engine.Types](id: Observation.Id, user: Option[UserDetails], step: Step.Id, v: Boolean) extends UserEvent[D]
final case class SkipMark[D<:Engine.Types](id: Observation.Id, user: Option[UserDetails], step: Step.Id, v: Boolean) extends UserEvent[D]
final case class Poll[D<:Engine.Types](clientId: ClientId) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
// Generic event to put a function in the main Stream process, which takes an
// action depending on the current state
final case class GetState[D<:Engine.Types](f: D#StateType => Option[Stream[IO, Event[D]]]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
// Generic event to put a function in the main Process process, which changes the state
// depending on the current state
final case class ModifyState[D<:Engine.Types](f: Handle[D#StateType, Event[D], D#EventData]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
// Calls a user given function in the main Stream process to stop an Action.
// It sets the Sequence to be stopped. The user function is called only if the Sequence is running.
final case class ActionStop[D <: Engine.Types](id: Observation.Id, f: D#StateType => Option[Stream[IO, Event[D]]]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

// Uses `cont` to resume execution of a paused Action. If the Action is not paused, it does nothing.
final case class ActionResume[D<:Engine.Types](id: Observation.Id, i: Int, cont: Stream[IO,
  Result]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

final case class LogDebug[D<:Engine.Types](msg: String) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

final case class LogInfo[D<:Engine.Types](msg: String) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

final case class LogWarning[D<:Engine.Types](msg: String) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

final case class LogError[D<:Engine.Types](msg: String) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
