// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.forms

import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.vdom.html_<^._
import react.common.ReactPropsWithChildren
import japgolly.scalajs.react.CtorType

final case class FormLabel(
  text: String,
  htmlFor: Option[String]
) extends ReactPropsWithChildren {
  @inline def render: Seq[CtorType.ChildArg] => VdomElement = FormLabel.component(this)
}

object FormLabel {
  type Props = FormLabel

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  protected val component = ScalaComponent.builder[Props]("FormLabel")
    .stateless
    .renderPC((_, p, c) =>
      <.label(
        ^.htmlFor :=? p.htmlFor,
        p.text,
        c
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}