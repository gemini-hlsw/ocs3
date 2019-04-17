// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import java.time.LocalDate

import cats.implicits._
import cats.effect._
import fs2.Stream
import giapi.client.gpi.GpiClient
import giapi.client.ghost.GhostClient
import gem.Observation
import gem.enum.Site

import scala.concurrent.ExecutionContext
import seqexec.engine.{Action, Result, Sequence}
import seqexec.model.enum.Instrument.GmosS
import seqexec.model.{ActionType, SequenceState, StepConfig}
import seqexec.server.SeqTranslate.ObserveContext
import seqexec.server.keywords.DhsClientSim
import seqexec.server.keywords.GdsClient
import seqexec.server.flamingos2.Flamingos2ControllerSim
import seqexec.server.gcal.GcalControllerSim
import seqexec.server.gmos.GmosControllerSim
import seqexec.server.gnirs.GnirsControllerSim
import seqexec.server.gsaoi.GsaoiControllerSim
import seqexec.server.tcs.{GuideConfigDb, TcsControllerSim}
import seqexec.server.gpi.GpiController
import seqexec.server.Response.Observed
import seqexec.server.ghost.GhostController
import seqexec.server.niri.NiriControllerSim
import seqexec.server.nifs.NifsControllerSim
import edu.gemini.spModel.core.Peer
import org.scalatest.FlatSpec
import org.http4s.Uri._
import seqexec.server.altair.AltairControllerSim
import squants.time.Seconds

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class SeqTranslateSpec extends FlatSpec {

  implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val csTimer: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private val config: StepConfig = Map()
  private val fileId = "DummyFileId"
  private val seqId = Observation.Id.unsafeFromString("GS-2018A-Q-1-1")
  private def observeActions(state: Action.ActionState): List[Action[IO]] = List(
    Action(ActionType.Observe, Stream.emit(Result.OK(Observed(fileId))).covary[IO],
      Action.State(state, Nil))
  )
  private val seqg = SequenceGen(
    seqId,
    "",
    GmosS,
    List(SequenceGen.PendingStepGen(
      1,
      config,
      Set(GmosS),
      SequenceGen.StepActionsGen(List(), Map(), _ => List(observeActions(Action.Idle)))
    ))
  )

  private val baseState: EngineState = (ODBSequencesLoader.loadSequenceEndo(seqId, seqg) >>>
    (EngineState.sequenceStateIndex(seqId) ^|-> Sequence.State.status).set(
      SequenceState.Running.init))(EngineState.default)

  // Observe started
  private val s0: EngineState = EngineState.sequenceStateIndex(seqId)
    .modify(_.start(0))(baseState)
  // Observe pending
  private val s1: EngineState = baseState
  // Observe completed
  private val s2: EngineState = EngineState.sequenceStateIndex(seqId)
    .modify(_.mark(0)(Result.OK(Observed(fileId))))(baseState)
  // Observe started, but with file Id already allocated
  private val s3: EngineState = EngineState.sequenceStateIndex(seqId)
    .modify(_.start(0).mark(0)(Result.Partial(FileIdAllocated(fileId))))(baseState)
  // Observe paused
  private val s4: EngineState = EngineState.sequenceStateIndex(seqId)
    .modify(_.mark(0)(Result.Paused(ObserveContext(_ => SeqAction(Result.OK(Observed
    (fileId))), Seconds(1)))))(baseState)
  // Observe failed
  private val s5: EngineState = EngineState.sequenceStateIndex(seqId)
    .modify(_.mark(0)(Result.Error("error")))(baseState)

  val gpiSim = GpiClient.simulatedGpiClient.use(x => IO(GpiController(x,
    new GdsClient(GdsClient.alwaysOkClient, uri("http://localhost:8888/xmlrpc"))))
  ).unsafeRunSync
  val ghostSim = GhostClient.simulatedGhostClient.use(x => IO(GhostController(x,
    new GdsClient(GdsClient.alwaysOkClient, uri("http://localhost:8888/xmlrpc"))))
  ).unsafeRunSync

  val guideDb = new GuideConfigDb[IO] {
    override def value: IO[GuideConfigDb.GuideConfig] = GuideConfigDb.defaultGuideConfig.pure[IO]

    override def set(v: GuideConfigDb.GuideConfig): IO[Unit] = IO.unit
  }

  private val systems = Systems[IO](
    new OdbProxy(new Peer("localhost", 8443, null), new OdbProxy.DummyOdbCommands),
    DhsClientSim(LocalDate.of(2016, 4, 15)),
    TcsControllerSim,
    GcalControllerSim,
    Flamingos2ControllerSim[IO],
    GmosControllerSim.south[IO],
    GmosControllerSim.north[IO],
    GnirsControllerSim[IO],
    GsaoiControllerSim[IO],
    gpiSim,
    ghostSim,
    NiriControllerSim[IO],
    NifsControllerSim[IO],
    AltairControllerSim,
    guideDb
  )

  private val translatorSettings = TranslateSettings(tcsKeywords = false, f2Keywords = false, gwsKeywords = false,
    gcalKeywords = false, gmosKeywords = false, gnirsKeywords = false, niriKeywords = false, nifsKeywords = false, altairKeywords = false)

  private val translator = SeqTranslate(Site.GS, systems, translatorSettings)

  "SeqTranslate" should "trigger stopObserve command only if exposure is in progress" in {
    assert(translator.stopObserve(seqId).apply(s0).isDefined)
    assert(translator.stopObserve(seqId).apply(s1).isEmpty)
    assert(translator.stopObserve(seqId).apply(s2).isEmpty)
    assert(translator.stopObserve(seqId).apply(s3).isDefined)
    assert(translator.stopObserve(seqId).apply(s4).isDefined)
    assert(translator.stopObserve(seqId).apply(s5).isEmpty)
  }

  "SeqTranslate" should "trigger abortObserve command only if exposure is in progress" in {
    assert(translator.abortObserve(seqId).apply(s0).isDefined)
    assert(translator.abortObserve(seqId).apply(s1).isEmpty)
    assert(translator.abortObserve(seqId).apply(s2).isEmpty)
    assert(translator.abortObserve(seqId).apply(s3).isDefined)
    assert(translator.abortObserve(seqId).apply(s4).isDefined)
    assert(translator.abortObserve(seqId).apply(s5).isEmpty)
  }

}
