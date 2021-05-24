// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import cats.{ Applicative, Monoid }
import cats.effect.{ ContextShift, IO, Timer }
import cats.syntax.all._
import cats.data.NonEmptyList
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.log4cats.Logger
import java.util.UUID

import edu.gemini.spModel.core.Peer
import seqexec.model.Observation
import lucuma.core.enum.Site
import giapi.client.ghost.GhostClient
import giapi.client.gpi.GpiClient
import org.http4s.Uri
import org.http4s.Uri.uri
import seqexec.engine
import seqexec.engine.{ Action, Result }
import seqexec.engine.Result.PauseContext
import seqexec.engine.Result.PartialVal
import seqexec.model.{ ActionType, ClientId }
import seqexec.model.enum.{ Instrument, Resource }
import seqexec.model.dhs._
import seqexec.model.SystemOverrides
import seqexec.model.config._
import seqexec.server.altair.{ AltairControllerSim, AltairKeywordReaderDummy }
import seqexec.server.flamingos2.Flamingos2ControllerSim
import seqexec.server.gcal.{ DummyGcalKeywordsReader, GcalControllerSim }
import seqexec.server.gems.{ GemsControllerSim, GemsKeywordReaderDummy }
import seqexec.server.ghost.GhostController
import seqexec.server.gmos.{ GmosControllerSim, GmosKeywordReaderDummy }
import seqexec.server.gnirs.{ GnirsControllerSim, GnirsKeywordReaderDummy }
import seqexec.server.gpi.GpiController
import seqexec.server.gsaoi.{ GsaoiControllerSim, GsaoiKeywordReaderDummy }
import seqexec.server.gws.DummyGwsKeywordsReader
import seqexec.server.keywords.{ DhsClientSim, GdsClient }
import seqexec.server.nifs.{ NifsControllerSim, NifsKeywordReaderDummy }
import seqexec.server.niri.{ NiriControllerSim, NiriKeywordReaderDummy }
import seqexec.server.tcs.{
  DummyTcsKeywordsReader,
  GuideConfigDb,
  TcsNorthControllerSim,
  TcsSouthControllerSim
}
import shapeless.tag

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object TestCommon {

  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit val ioTimer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  implicit val logger: Logger[IO] = NoOpLogger.impl[IO]

  val defaultSettings: SeqexecEngineConfiguration = SeqexecEngineConfiguration(
    odb = uri("localhost"),
    dhsServer = uri("http://localhost/"),
    systemControl = SystemsControlConfiguration(
      altair = ControlStrategy.Simulated,
      gems = ControlStrategy.Simulated,
      dhs = ControlStrategy.Simulated,
      f2 = ControlStrategy.Simulated,
      gcal = ControlStrategy.Simulated,
      gmos = ControlStrategy.Simulated,
      gnirs = ControlStrategy.Simulated,
      gpi = ControlStrategy.Simulated,
      gpiGds = ControlStrategy.Simulated,
      ghost = ControlStrategy.Simulated,
      ghostGds = ControlStrategy.Simulated,
      gsaoi = ControlStrategy.Simulated,
      gws = ControlStrategy.Simulated,
      nifs = ControlStrategy.Simulated,
      niri = ControlStrategy.Simulated,
      tcs = ControlStrategy.Simulated
    ),
    odbNotifications = false,
    instForceError = false,
    failAt = 0,
    10.seconds,
    tag[GpiSettings][Uri](uri("vm://localhost:8888")),
    tag[GpiSettings][Uri](uri("http://localhost:8888")),
    tag[GhostSettings][Uri](uri("vm://localhost:8888")),
    tag[GhostSettings][Uri](uri("http://localhost:8888")),
    "",
    Some("127.0.0.1"),
    0,
    3.seconds,
    10.seconds
  )

  def configure[F[_]: Applicative](resource: Resource): F[Result[F]] =
    Result.OK(Response.Configured(resource)).pure[F].widen

  def pendingAction[F[_]: Applicative](resource: Resource): Action[F] =
    engine.fromF[F](ActionType.Configure(resource), configure(resource))

  def running[F[_]: Applicative](resource: Resource): Action[F] =
    Action.state[F].set(Action.State(Action.ActionState.Started, Nil))(pendingAction(resource))

  def done[F[_]: Applicative](resource: Resource): Action[F] =
    Action
      .state[F]
      .set(Action.State(Action.ActionState.Completed(Response.Configured(resource)), Nil))(
        pendingAction(resource)
      )

  private val fileId = toImageFileId("fileId")

  def observing[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(Action.State(Action.ActionState.Started, Nil))(
        engine.fromF[F](ActionType.Observe, Result.OK(Response.Observed(fileId)).pure[F].widen)
      )

  final case class PartialValue(s: String) extends PartialVal

  def observingPartial[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(Action.State(Action.ActionState.Started, Nil))(
        engine.fromF[F](ActionType.Observe,
                        Result.Partial(PartialValue("Value")).pure[F].widen,
                        Result.OK(Response.Ignored).pure[F].widen
        )
      )

  def fileIdReady[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId))))(observing)

  def observed[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(
        Action.State(Action.ActionState.Completed(Response.Observed(fileId)),
                     List(FileIdAllocated(fileId))
        )
      )(observing)

  def observePartial[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(Action.State(Action.ActionState.Started, List(FileIdAllocated(fileId))))(
        observingPartial
      )

  def paused[F[_]: Applicative]: Action[F] =
    Action
      .state[F]
      .set(
        Action.State(Action.ActionState.Paused(new PauseContext[F] {}),
                     List(FileIdAllocated(fileId))
        )
      )(observing)

  def testCompleted(oid: Observation.Id)(st: EngineState[IO]): Boolean =
    st.sequences
      .get(oid)
      .exists(_.seq.status.isCompleted)

  private val sm = SeqexecMetrics.build[IO](Site.GS, new CollectorRegistry()).unsafeRunSync()

  private val gpiSim: IO[GpiController[IO]] = GpiClient
    .simulatedGpiClient[IO]
    .use(x =>
      IO(
        GpiController(x, GdsClient(GdsClient.alwaysOkClient[IO], uri("http://localhost:8888")))
      )
    )

  private val ghostSim: IO[GhostController[IO]] = GhostClient
    .simulatedGhostClient[IO]
    .use(x =>
      IO(
        GhostController(x, GdsClient(GdsClient.alwaysOkClient[IO], uri("http://localhost:8888")))
      )
    )

  val defaultSystems: Systems[IO] = (DhsClientSim[IO],
                                     Flamingos2ControllerSim[IO],
                                     GmosControllerSim.south[IO],
                                     GmosControllerSim.north[IO],
                                     GnirsControllerSim[IO],
                                     GsaoiControllerSim[IO],
                                     gpiSim,
                                     ghostSim,
                                     NiriControllerSim[IO],
                                     NifsControllerSim[IO]
  ).mapN { (dhs, f2, gmosS, gmosN, gnirs, gsaoi, gpi, ghost, niri, nifs) =>
    Systems[IO](
      OdbProxy(new Peer("localhost", 8443, null), new OdbProxy.DummyOdbCommands),
      dhs,
      TcsSouthControllerSim[IO],
      TcsNorthControllerSim[IO],
      GcalControllerSim[IO],
      f2,
      gmosS,
      gmosN,
      gnirs,
      gsaoi,
      gpi,
      ghost,
      niri,
      nifs,
      AltairControllerSim[IO],
      GemsControllerSim[IO],
      GuideConfigDb.constant[IO],
      DummyTcsKeywordsReader[IO],
      DummyGcalKeywordsReader[IO],
      GmosKeywordReaderDummy[IO],
      GnirsKeywordReaderDummy[IO],
      NiriKeywordReaderDummy[IO],
      NifsKeywordReaderDummy[IO],
      GsaoiKeywordReaderDummy[IO],
      AltairKeywordReaderDummy[IO],
      GemsKeywordReaderDummy[IO],
      DummyGwsKeywordsReader[IO]
    )
  }.unsafeRunSync()

  val seqexecEngine: SeqexecEngine[IO] =
    SeqexecEngine.build(Site.GS, defaultSystems, defaultSettings, sm).unsafeRunSync()

  def advanceOne(
    q:   EventQueue[IO],
    s0:  EngineState[IO],
    put: IO[Unit]
  ): IO[Option[EngineState[IO]]] =
    advanceN(q, s0, put, 1L)

  def advanceN(
    q:   EventQueue[IO],
    s0:  EngineState[IO],
    put: IO[Unit],
    n:   Long
  ): IO[Option[EngineState[IO]]] =
    (put *> seqexecEngine.stream(q.dequeue)(s0).take(n).compile.last).map(_.map(_._2))

  val seqId1: String            = "GS-2018B-Q-0-1"
  val seqObsId1: Observation.Id = Observation.Id.unsafeFromString(seqId1)
  val seqId2: String            = "GS-2018B-Q-0-2"
  val seqObsId2: Observation.Id = Observation.Id.unsafeFromString(seqId2)
  val seqId3: String            = "GS-2018B-Q-0-3"
  val seqObsId3: Observation.Id = Observation.Id.unsafeFromString(seqId3)
  val clientId                  = ClientId(UUID.randomUUID)

  def sequence(id: Observation.Id): SequenceGen[IO] = SequenceGen[IO](
    id = id,
    title = "",
    instrument = Instrument.F2,
    steps = List(
      SequenceGen.PendingStepGen(
        id = 1,
        Monoid.empty[DataId],
        config = CleanConfig.empty,
        resources = Set.empty,
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = Map(),
          post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.F2)))
        )
      )
    )
  )

  def sequenceNSteps(id: Observation.Id, n: Int): SequenceGen[IO] = SequenceGen[IO](
    id = id,
    title = "",
    instrument = Instrument.F2,
    steps = List
      .range(1, n)
      .map(
        SequenceGen.PendingStepGen(
          _,
          Monoid.empty[DataId],
          config = CleanConfig.empty,
          resources = Set.empty,
          _ => InstrumentSystem.Uncontrollable,
          generator = SequenceGen.StepActionsGen(
            configs = Map.empty,
            post = (_, _) => List(NonEmptyList.one(pendingAction[IO](Instrument.F2)))
          )
        )
      )
  )

  def sequenceWithResources(
    id:        Observation.Id,
    ins:       Instrument,
    resources: Set[Resource]
  ): SequenceGen[IO] = SequenceGen[IO](
    id = id,
    title = "",
    instrument = ins,
    steps = List(
      SequenceGen.PendingStepGen(
        id = 1,
        Monoid.empty[DataId],
        config = CleanConfig.empty,
        resources = resources,
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = resources.map(r => r -> { _: SystemOverrides => pendingAction[IO](r) }).toMap,
          post = (_, _) => Nil
        )
      ),
      SequenceGen.PendingStepGen(
        id = 2,
        Monoid.empty[DataId],
        config = CleanConfig.empty,
        resources = resources,
        _ => InstrumentSystem.Uncontrollable,
        generator = SequenceGen.StepActionsGen(
          configs = resources.map(r => r -> { _: SystemOverrides => pendingAction[IO](r) }).toMap,
          post = (_, _) => Nil
        )
      )
    )
  )

}
