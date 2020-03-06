// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.sequence.steps

import cats.implicits._
import japgolly.scalajs.react._
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.vdom.html_<^._
import gem.Observation
import react.common._
import seqexec.model.Step
import seqexec.model.StepState
import seqexec.web.client.components.SeqexecStyles
import seqexec.web.client.model.ClientStatus
import react.semanticui.elements.icon.Icon
import react.semanticui.elements.icon.Icon._
import seqexec.web.client.services.HtmlConstants.iconEmpty
import seqexec.web.client.reusability._

/**
  * Component to display an icon for the state
  */
final case class StepToolsCell(
  clientStatus:       ClientStatus,
  step:               Step,
  rowHeight:          Int,
  secondRowHeight:    Int,
  isPreview:          Boolean,
  nextStepToRun:      Option[Int],
  obsId:              Observation.Id,
  firstRunnableIndex: Int,
  breakPointEnterCB:  Int => Callback,
  breakPointLeaveCB:  Int => Callback,
  heightChangeCB:     Int => Callback
) extends ReactProps {
  @inline def render: VdomElement = StepToolsCell.component(this)
}

object StepToolsCell {
  type Props = StepToolsCell

  implicit val propsReuse: Reusability[Props] =
    Reusability.caseClassExcept[Props](Symbol("heightChangeCB"),
                                       Symbol("breakPointEnterCB"),
                                       Symbol("breakPointLeaveCB"))

  protected val component = ScalaComponent
    .builder[Props]("StepToolsCell")
    .stateless
    .render_P { p =>
      <.div(
        SeqexecStyles.controlCell,
        StepBreakStopCell(
          p.clientStatus,
          p.step,
          p.rowHeight,
          p.obsId,
          p.firstRunnableIndex,
          p.breakPointEnterCB,
          p.breakPointLeaveCB,
          p.heightChangeCB
        ).when(p.clientStatus.isLogged)
         .unless(p.isPreview),
        StepIconCell(
          p.step.status,
          p.step.skip,
          p.nextStepToRun.forall(_ === p.step.id),
          p.rowHeight - p.secondRowHeight
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build
}

/**
  * Component to display an icon for the state
  */
final case class StepIconCell(
  status: StepState,
  skip: Boolean,
  nextToRun: Boolean,
  height: Int
) extends ReactProps {
  @inline def render: VdomElement = StepIconCell.component(this)
}

object StepIconCell {
  type Props = StepIconCell

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  private def stepIcon(p: Props): VdomNode =
    p.status match {
      case StepState.Completed => IconCheckmark
      case StepState.Running   => IconCircleNotched.copyIcon(loading = true)
      case StepState.Failed(_) => IconAttention
      case StepState.Skipped =>
        IconReply.copyIcon(fitted  = true,
                           rotated = Icon.Rotated.CounterClockwise)
      case _ if p.skip =>
        IconReply.copyIcon(fitted  = true,
                           rotated = Icon.Rotated.CounterClockwise)
      case _ if p.nextToRun => IconChevronRight
      case _                => iconEmpty
    }

  private def stepStyle(p: Props): Css =
    p.status match {
      case StepState.Running   => SeqexecStyles.runningIconCell
      case StepState.Skipped   => SeqexecStyles.skippedIconCell
      case StepState.Completed => SeqexecStyles.completedIconCell
      case StepState.Failed(_) => SeqexecStyles.errorCell
      case _ if p.skip         => SeqexecStyles.skippedIconCell
      case _                   => SeqexecStyles.iconCell
    }

  protected val component = ScalaComponent
    .builder[Props]("StepIconCell")
    .stateless
    .render_P(
      p =>
        <.div(
          ^.height := p.height.px,
          stepStyle(p),
          stepIcon(p)
      ))
    .configure(Reusability.shouldComponentUpdate)
    .build
}
