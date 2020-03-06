// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components

import cats.implicits._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import monocle.macros.Lenses

import scala.concurrent.ExecutionContext.Implicits.global
import seqexec.model.UserDetails
import seqexec.web.client.model._
import seqexec.web.client.model.SectionVisibilityState._
import seqexec.web.client.actions.CloseLoginBox
import seqexec.web.client.actions.LoggedIn
import seqexec.web.client.circuit.SeqexecCircuit
// import react.semanticui.collections.form._
import react.semanticui.collections.grid._
import react.semanticui.colors._
import react.semanticui.floats
import react.semanticui.textalignment._
import react.semanticui.verticalalignment._
import react.semanticui.widths._
import react.semanticui.elements.button.Button
import react.semanticui.elements.icon.Icon
import react.semanticui.modules.modal._
import seqexec.web.client.components.forms.FormLabel
import seqexec.web.client.services.SeqexecWebClient
import react.common._
import seqexec.web.client.reusability._

/**
  * UI for the login box
  */
final case class LoginBox(
  visible: SectionVisibilityState
) extends ReactProps {
  @inline def render: VdomElement = LoginBox.component(this)
}

object LoginBox {
  type Props = LoginBox

  @Lenses
  final case class State(username:    String,
                         password:    String,
                         progressMsg: Option[String],
                         errorMsg:    Option[String])

  object State {
    val Empty: State = State("", "", None, None)
  }

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]
  implicit val stateReuse: Reusability[State] = Reusability.derive[State]

  private val formId = "login"

  class Backend(b: BackendScope[Props, State]) {
    def pwdMod(e: ReactEventFromInput): CallbackTo[Unit] = {
      // Capture the value outside setState, react reuses the events
      val v = e.target.value
      b.modState(State.password.set(v))
    }

    def userMod(e: ReactEventFromInput): CallbackTo[Unit] = {
      val v = e.target.value
      b.modState(State.username.set(v))
    }

    def loggedInEvent(u: UserDetails): Callback =
      b.setState(State.Empty) >> SeqexecCircuit.dispatchCB(LoggedIn(u))
    def updateProgressMsg(m: String): Callback =
      b.modState(State.progressMsg.set(m.some) >>> State.errorMsg.set(none))
    def updateErrorMsg(m: String): Callback =
      b.modState(State.errorMsg.set(m.some) >>> State.progressMsg.set(none))
    def closeBox: Callback =
      b.setState(State.Empty) >> SeqexecCircuit.dispatchCB(CloseLoginBox)

    def attemptLogin: Callback = b.state >>= { s =>
      // Change the UI and call login on the remote backend
      updateProgressMsg("Authenticating...") >>
        Callback.future(
          SeqexecWebClient
            .login(s.username, s.password)
            .map(loggedInEvent)
            .recover {
              case _: Exception =>
                updateErrorMsg("Login failed, check username/password")
            }
        )
    }

    private def toolbar(s: State): ModalActions =
      ModalActions(
        Grid(
          GridRow(verticalAlign = Middle)(
            List[Option[VdomNode]](
              s.progressMsg.map(m =>
                  GridColumn(
                    textAlign = Left,
                    floated   = floats.Left,
                    width     = Six
                  )(
                    Icon(name = "circle notched", loading = true),
                    m
                )
              ),
              s.errorMsg.map(m =>
                  GridColumn(
                    textAlign = Left,
                    floated   = floats.Left,
                    width     = Six,
                    color     = Red
                  )(                
                    Icon("attention"),
                    m
                )
              ),
              (
                GridColumn(
                  textAlign = Right,
                  floated   = floats.Right,
                  width     = Ten
                )(
                  Button(onClick    = closeBox)("Cancel"),
                  Button(onClick    = attemptLogin,
                          // buttonType = Button.SubmitType,
                          // form       = Some(formId)
                          )("Login")
              ): VdomNode).some
            ).flatten: _*
          )
        )
      )

    def render(p: Props, s: State) =
      Modal(
        open = p.visible === SectionOpen,
        header = ModalHeader("Login").cprops,
        content = ModalContent(
          <.form(
            ^.cls := "ui form",
            ^.id := formId,
            ^.method := "post",
            ^.action := "#",
            <.div(
              ^.cls := "required field",
              FormLabel("Username", Some("username")),
              <.div(
                ^.cls := "ui icon input",
                <.input(
                  ^.`type` := "text",
                  ^.placeholder := "Username",
                  ^.name := "username",
                  ^.id := "username",
                  ^.value := s.username,
                  ^.onChange ==> userMod
                ),
                Icon("user")
              )
            ),
            <.div(
              ^.cls := "required field",
              FormLabel("Password", Some("password")),
              <.div(
                ^.cls := "ui icon input",
                <.input(
                  ^.`type` := "password",
                  ^.placeholder := "Password",
                  ^.name := "password",
                  ^.id := "password",
                  ^.value := s.password,
                  ^.onChange ==> pwdMod
                ),
                Icon("lock")
              )
            )
          )
        ).cprops,
        actions = toolbar(s).cprops
      )
  }

  val component = ScalaComponent
    .builder[Props]("Login")
    .initialState(State.Empty)
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdate)
    .build
}
