// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.sequence.steps

import cats.implicits._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.Reusability
import japgolly.scalajs.react.extra.Reusability._
import gem.Observation
import scala.collection.immutable.SortedMap
import seqexec.model.enum._
import seqexec.model.StepId
import seqexec.web.client.actions.RequestResourceRun
import seqexec.web.client.circuit.SeqexecCircuit
import seqexec.web.client.components.SeqexecStyles
import seqexec.web.client.model.ResourceRunOperation
import seqexec.web.client.semanticui.elements.button.Button
import seqexec.web.client.semanticui.elements.popup.Popup
import seqexec.web.client.semanticui.Size
import seqexec.web.client.reusability._
import web.client.style._

/**
  * Contains the control buttons for each subsystem
  */
object SubsystemControlCell {
  final case class Props(
    id:             Observation.Id,
    stepId:         Int,
    resources:      List[Resource],
    resourcesCalls: SortedMap[Resource, ResourceRunOperation])

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def requestResourceCall(
    id:     Observation.Id,
    stepId: StepId,
    r:      Resource
  ): Callback = SeqexecCircuit.dispatchCB(RequestResourceRun(id, stepId, r))

  private val component = ScalaComponent
    .builder[Props]("SubsystemControl")
    .render_P { p =>
      <.div(
        SeqexecStyles.notInMobile,
        p.resources.map { r =>
          Popup(
            Popup.Props("button", s"Configure ${r.show}"),
            Button(
              Button.Props(
                size  = Size.Small,
                color = Some("blue"),
                disabled = p.resourcesCalls
                  .get(r)
                  .map(_ === ResourceRunOperation.ResourceRunInFlight)
                  .getOrElse(false),
                onClick = requestResourceCall(p.id, p.stepId, r)
              ),
              r.show
            )
          )
        }.toTagMod
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(p: Props): Unmounted[Props, Unit, Unit] = component(p)
}