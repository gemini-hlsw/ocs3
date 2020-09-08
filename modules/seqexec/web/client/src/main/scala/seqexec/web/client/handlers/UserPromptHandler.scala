// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.handlers

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import cats.syntax.all._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import seqexec.model.events.UserPromptNotification
import seqexec.web.client.actions._
import seqexec.web.client.model._
import seqexec.model.UserPrompt

class UserPromptHandler[M](modelRW: ModelRW[M, UserPromptState])
    extends ActionHandler(modelRW)
    with Handlers[M, UserPromptState] {
  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserPromptNotification(not, _)) =>
      // Update the notification state
      val lens         = UserPromptState.notification.set(not.some)
      // Update the model as load failed
      val modelUpdateE = not match {
        case UserPrompt.TargetCheckOverride(id, _, _) => Effect(Future(RunStartFailed(id)))
      }
      updatedLE(lens, modelUpdateE)
  }

  def handleClosePrompt: PartialFunction[Any, ActionResult[M]] = {
    case CloseUserPromptBox(x) =>
      val overrideEffect = this.value.notification match {
        case Some(UserPrompt.TargetCheckOverride(id, _, _)) if x === UserPromptResult.Cancel =>
          Effect(Future(RequestRun(id, RunOptions.TargetCheckOverride)))
        case _                                                                               => VoidEffect
      }
      updatedLE(UserPromptState.notification.set(none), overrideEffect)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUserNotification, handleClosePrompt).combineAll
}
