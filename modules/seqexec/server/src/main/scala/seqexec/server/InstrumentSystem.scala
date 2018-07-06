// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import cats.effect.IO
import seqexec.model.dhs.ImageFileId
import seqexec.server.keywords.{DhsInstrument, KeywordsClient}
import edu.gemini.spModel.config2.Config
import squants.Time

trait InstrumentSystem[F[_]] extends System[F] {
  // The name used for this instrument in the science fold configuration
  val sfName: String
  val contributorName: String
  val observeControl: InstrumentSystem.ObserveControl
  def observe(config: Config): SeqObserveF[F, ImageFileId, ObserveCommand.Result]
  //Expected total observe lapse, used to calculate timeout
  def calcObserveTime(config: Config): Time

  override def notifyObserveStart = SeqAction.void
}

object InstrumentSystem {

  implicit val HeaderProvider: HeaderProvider[InstrumentSystem[IO]] = new HeaderProvider[InstrumentSystem[IO]] {
    def name(a: InstrumentSystem[IO]): String = a match {
      case i: DhsInstrument => i.dhsInstrumentName
      case _                => sys.error("Missing instrument")
    }
    def keywordsClient(a: InstrumentSystem[IO]): KeywordsClient = a match {
      case u: DhsInstrument => u
      case _                => sys.error("Missing instrument")
    }
  }
  sealed trait ObserveControl
  object Uncontrollable extends ObserveControl
  final case class StopObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class AbortObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class PauseObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class ContinuePausedCmd(self: Time => SeqAction[ObserveCommand.Result]) extends AnyVal
  final case class StopPausedCmd(self: SeqAction[ObserveCommand.Result]) extends AnyVal
  final case class AbortPausedCmd(self: SeqAction[ObserveCommand.Result]) extends AnyVal
  final case class OpticControl(stop: StopObserveCmd,
                                abort: AbortObserveCmd,
                                pause: PauseObserveCmd,
                                continue: ContinuePausedCmd,
                                stopPaused: StopPausedCmd,
                                abortPaused: AbortPausedCmd) extends ObserveControl
  // Special class for infrared instrument, because they cannot pause/resume
  final case class InfraredControl(stop: StopObserveCmd,
                                   abort: AbortObserveCmd) extends ObserveControl
}
