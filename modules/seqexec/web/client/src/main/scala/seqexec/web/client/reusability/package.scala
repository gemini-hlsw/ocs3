// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client

import diode.data.PotState
import cats.implicits._
import gem.Observation
import gem.util.Enumerated
import japgolly.scalajs.react.CatsReact._
import japgolly.scalajs.react.Reusability
import scala.collection.immutable.SortedMap
import seqexec.model.enum.Resource
import seqexec.model.enum.ServerLogLevel
import seqexec.model.dhs._
import seqexec.model.Observer
import seqexec.model.QueueId
import seqexec.model.Step
import seqexec.model.StepConfig
import seqexec.model.StepState
import seqexec.model.UserDetails
import seqexec.model.SequenceState
import seqexec.model.M1GuideConfig
import seqexec.model.M2GuideConfig
import seqexec.model.TelescopeGuideConfig
import seqexec.web.client.model.AvailableTab
import seqexec.web.client.model.ClientStatus
import seqexec.web.client.model.SectionVisibilityState
import seqexec.web.client.model.UserNotificationState
import seqexec.web.client.model.WebSocketConnection
import seqexec.web.client.model.PauseOperation
import seqexec.web.client.model.QueueOperations
import seqexec.web.client.model.RunOperation
import seqexec.web.client.model.SyncOperation
import seqexec.web.client.model.TabOperations
import seqexec.web.client.model.ResourceRunOperation
import seqexec.web.client.model.StartFromOperation
import seqexec.web.client.model.TabSelected
import seqexec.web.client.model.SoundSelection
import seqexec.web.client.model.GlobalLog
import seqexec.web.client.circuit._

package object reusability {
  implicit def enumeratedReuse[A <: AnyRef: Enumerated]: Reusability[A] =
    Reusability.byRef
  implicit val imageIdReuse: Reusability[ImageFileId]       = Reusability.byEq
  implicit val stepStateReuse: Reusability[StepState]       = Reusability.byEq
  implicit val obsIdReuse: Reusability[Observation.Id]      = Reusability.byEq
  implicit val observerReuse: Reusability[Observer]         = Reusability.byEq
  implicit val stepConfigReuse: Reusability[StepConfig]     = Reusability.byEq
  implicit val stepReuse: Reusability[Step]                 = Reusability.byEq
  implicit val seqStateReuse: Reusability[SequenceState]    = Reusability.byEq
  implicit val clientStatusReuse: Reusability[ClientStatus] = Reusability.byEq
  implicit val stepTTReuse: Reusability[StepsTableTypeSelection] =
    Reusability.byEq
  implicit val stTbFocusReuse: Reusability[StepsTableFocus] = Reusability.byEq
  implicit val stASFocusReuse: Reusability[StatusAndStepFocus] =
    Reusability.byEq
  implicit val sCFocusReuse: Reusability[SequenceControlFocus] =
    Reusability.byEq
  implicit val tabSelReuse: Reusability[TabSelected] = Reusability.byRef
  implicit val sectReuse: Reusability[SectionVisibilityState] =
    Reusability.byRef
  implicit val potStateReuse: Reusability[PotState] = Reusability.byRef
  implicit val webSCeuse: Reusability[WebSocketConnection] =
    Reusability.by(_.ws.state)
  implicit val runOperationReuse: Reusability[RunOperation] = Reusability.byRef
  implicit val syncOperationReuse: Reusability[SyncOperation] =
    Reusability.byRef
  implicit val psOperationReuse: Reusability[PauseOperation] = Reusability.byRef
  implicit val rrOperationReuse: Reusability[ResourceRunOperation] =
    Reusability.byRef
  implicit val rfOperationReuse: Reusability[StartFromOperation] =
    Reusability.byRef
  implicit val availableTabsReuse: Reusability[AvailableTab] = Reusability.byEq
  implicit val userDetailsReuse: Reusability[UserDetails]    = Reusability.byEq
  implicit val usrNotReuse: Reusability[UserNotificationState] =
    Reusability.byEq
  implicit val qoReuse: Reusability[QueueOperations]       = Reusability.byEq
  implicit val qfReuse: Reusability[CalQueueControlFocus]  = Reusability.byEq
  implicit val cqfReuse: Reusability[CalQueueFocus]        = Reusability.byEq
  implicit val qidReuse: Reusability[QueueId]              = Reusability.byEq
  implicit val soundReuse: Reusability[SoundSelection]     = Reusability.byRef
  implicit val globalLogReuse: Reusability[GlobalLog]      = Reusability.byEq
  implicit val resMap: Reusability[Map[Resource, ResourceRunOperation]] =
    Reusability.map
  implicit val sllbMap: Reusability[Map[ServerLogLevel, Boolean]] =
    Reusability.map
  implicit val resSMap: Reusability[SortedMap[Resource, ResourceRunOperation]] =
    Reusability.by(_.toMap)
  implicit val tabOpsMap: Reusability[TabOperations] =
    Reusability.byEq
  implicit val m1gReuse: Reusability[M1GuideConfig] =
    Reusability.derive[M1GuideConfig]
  implicit val m2gReuse: Reusability[M2GuideConfig] =
    Reusability.derive[M2GuideConfig]
  implicit val configReuse: Reusability[TelescopeGuideConfig] =
    Reusability.derive[TelescopeGuideConfig]
}
