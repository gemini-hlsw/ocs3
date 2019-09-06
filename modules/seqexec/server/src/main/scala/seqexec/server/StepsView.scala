// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import cats.implicits._
import cats.data.NonEmptyList
import seqexec.model.ActionType
import seqexec.model.Step
import seqexec.model.StandardStep
import seqexec.model.NodAndShuffleStep
import seqexec.model.NodAndShuffleStatus
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.ActionStatus
import seqexec.model.enum.Resource
import seqexec.model.enum.Instrument
import seqexec.model.enum.Instrument._
import seqexec.model.enum.SystemName
import seqexec.model.StepState
import seqexec.engine
import seqexec.engine.Action.ActionState
import seqexec.engine.Action
import seqexec.engine.ParallelActions
import seqexec.server.gmos.Gmos

sealed trait StepsView[F[_]] {
  /**
   * This method creates a view of the step for the client
   * The Step returned maybe a StandardStep of be specialized e.g. for N&S
   */
  def stepView(
    stepg: SequenceGen.StepGen[F],
    step: engine.Step[F],
    altCfgStatus: List[(Resource, ActionStatus)]
  ): Step
}

object StepsView {
  private def kindToResource(kind: ActionType): List[Resource] = kind match {
    case ActionType.Configure(r) => List(r)
    case _                       => Nil
  }

  def splitAfter[A](l: List[A])(p: A => Boolean): (List[A], List[A]) =
    l.splitAt(l.indexWhere(p) + 1)

  private[server] def separateActions[F[_]](ls: NonEmptyList[Action[F]]): (List[Action[F]], List[Action[F]]) =
    ls.toList.partition(_.state.runState match {
        case ActionState.Completed(_) => false
        case ActionState.Failed(_)    => false
        case _                        => true
      }
    )

  private def actionsToResources[F[_]](s: NonEmptyList[Action[F]]) =
    separateActions(s).bimap(_.map(_.kind).flatMap(kindToResource), _.map(_.kind).flatMap(kindToResource))

  private[server] def configStatus[F[_]](executions: List[ParallelActions[F]]): List[(Resource, ActionStatus)] = {
    // Remove undefined actions
    val ex = executions.filter { !separateActions(_)._2.exists(_.kind === ActionType.Undefined) }
    // Split where at least one is running
    val (current, pending) = splitAfter(ex)(separateActions(_)._1.nonEmpty)

    // Calculate the state up to the current
    val configStatus = current.foldLeft(Map.empty[Resource, ActionStatus]) {
      case (s, e) =>
        val (a, r) = separateActions(e).bimap(
            _.flatMap(a => kindToResource(a.kind).tupleRight(ActionStatus.Running)).toMap,
            _.flatMap(r => kindToResource(r.kind).tupleRight(ActionStatus.Completed)).toMap)
        s ++ a ++ r
    }

    // Find out systems in the future
    val presentSystems = configStatus.keys.toList
    // Calculate status of pending items
    val systemsPending = pending
      .map(actionsToResources)
      .flatMap { case(a, b) => a.tupleRight(ActionStatus.Pending) ::: b.tupleRight(ActionStatus.Completed) }
      .filter {
        case (a, _) => !presentSystems.contains(a)
      }.distinct

    (configStatus ++ systemsPending).toList.sortBy(_._1)
  }

  /**
   * Calculates the config status for pending steps
   */
  private[server] def pendingConfigStatus[F[_]](executions: List[ParallelActions[F]]): List[(Resource, ActionStatus)] =
    executions
      .map(actionsToResources)
      .flatMap { case (a, b) => a ::: b }
      .distinct
      .tupleRight(ActionStatus.Pending).sortBy(_._1)

  /**
   * Overall pending status for a step
   */
  private def stepConfigStatus[F[_]](step: engine.Step[F]): List[(Resource, ActionStatus)] =
    engine.Step.status(step) match {
      case StepState.Pending => pendingConfigStatus(step.executions)
      case _                 => configStatus(step.executions)
    }

  private def observeAction[F[_]](executions: List[ParallelActions[F]]): Option[Action[F]] =
    // FIXME This is too naive and doesn't work properly for N&S
    executions.flatMap(_.toList).filter(_.kind === ActionType.Observe).headOption

  private[server] def observeStatus[F[_]](executions: List[ParallelActions[F]]): ActionStatus =
    observeAction(executions)
      .map(_.state.runState.actionStatus)
      .getOrElse(ActionStatus.Pending)

  private def fileId[F[_]](executions: List[engine.ParallelActions[F]]): Option[ImageFileId] =
    observeAction(executions).flatMap(_.state.partials.collectFirst{
      case FileIdAllocated(fid) => fid
    })

  private def runningOrComplete[F[_]](status: StepState): Boolean =
    status match {
      case StepState.Completed | StepState.Running => true
      case _                                       => false
    }

  private def defaultStepsView[F[_]]: StepsView[F] = new StepsView[F] {
    def stepView(
      stepg: SequenceGen.StepGen[F],
      step: engine.Step[F],
      altCfgStatus: List[(Resource, ActionStatus)]
    ): Step = {
      val status = engine.Step.status(step)
      val configStatus =
        if (runningOrComplete(status)) {
          stepConfigStatus(step)
        } else {
          altCfgStatus
        }

      StandardStep(
        id = step.id,
        config = stepg.config,
        status = status,
        breakpoint = step.breakpoint.self,
        skip = step.skipMark.self,
        configStatus = configStatus,
        observeStatus = observeStatus(step.executions),
        fileId = fileId(step.executions).orElse(stepg.some.collect{
          case SequenceGen.CompletedStepGen(_, _, fileId) => fileId
        }.flatten))
    }

  }

  private def gmosStepsView[F[_]]: StepsView[F] = new StepsView[F] {
    def stepView(
      stepg: SequenceGen.StepGen[F],
      step: engine.Step[F],
      altCfgStatus: List[(Resource, ActionStatus)]
    ): Step = {
      // Not nice, At this stage we only have the raw config
      if (stepg.config.get(SystemName.Instrument).exists(_.get(Gmos.NSKey.getPath).exists(_ === "true"))) {
        val status = engine.Step.status(step)
        val configStatus =
          if (runningOrComplete(status)) {
            stepConfigStatus(step)
          } else {
            altCfgStatus
          }

        NodAndShuffleStep(
          id = step.id,
          config = stepg.config,
          status = status,
          breakpoint = step.breakpoint.self,
          skip = step.skipMark.self,
          configStatus = configStatus,
          nsStatus = NodAndShuffleStatus(observeStatus(step.executions)),
          fileId = fileId(step.executions).orElse(stepg.some.collect{
            case SequenceGen.CompletedStepGen(_, _, fileId) => fileId
          }.flatten))
      } else defaultStepsView.stepView(stepg, step, altCfgStatus)
    }

  }

  def stepsView[F[_]](instrument: Instrument): StepsView[F] = instrument match {
    case GmosN | GmosS => gmosStepsView[F]
    case _ => defaultStepsView[F]
  }
}
