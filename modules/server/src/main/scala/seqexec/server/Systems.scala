// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import cats.Monad
import cats.effect._
import cats.syntax.all._
import edu.gemini.epics.acm.CaService
import edu.gemini.spModel.core.Peer
import giapi.client.ghost.GhostClient
import giapi.client.gpi.GpiClient
import org.typelevel.log4cats.Logger
import lucuma.core.enum.Site
import mouse.boolean._
import org.http4s.client.Client
import seqexec.model.config._
import seqexec.server.altair._
import seqexec.server.flamingos2._
import seqexec.server.gcal._
import seqexec.server.gems._
import seqexec.server.ghost._
import seqexec.server.gmos._
import seqexec.server.gnirs._
import seqexec.server.gpi._
import seqexec.server.gsaoi._
import seqexec.server.gws.DummyGwsKeywordsReader
import seqexec.server.gws.GwsEpics
import seqexec.server.gws.GwsKeywordReader
import seqexec.server.gws.GwsKeywordsReaderEpics
import seqexec.server.keywords._
import seqexec.server.nifs._
import seqexec.server.niri._
import seqexec.server.tcs._

final case class Systems[F[_]](
  odb:                 OdbProxy[F],
  dhs:                 DhsClient[F],
  tcsSouth:            TcsSouthController[F],
  tcsNorth:            TcsNorthController[F],
  gcal:                GcalController[F],
  flamingos2:          Flamingos2Controller[F],
  gmosSouth:           GmosSouthController[F],
  gmosNorth:           GmosNorthController[F],
  gnirs:               GnirsController[F],
  gsaoi:               GsaoiController[F],
  gpi:                 GpiController[F],
  ghost:               GhostController[F],
  niri:                NiriController[F],
  nifs:                NifsController[F],
  altair:              AltairController[F],
  gems:                GemsController[F],
  guideDb:             GuideConfigDb[F],
  tcsKeywordReader:    TcsKeywordsReader[F],
  gcalKeywordReader:   GcalKeywordReader[F],
  gmosKeywordReader:   GmosKeywordReader[F],
  gnirsKeywordReader:  GnirsKeywordReader[F],
  niriKeywordReader:   NiriKeywordReader[F],
  nifsKeywordReader:   NifsKeywordReader[F],
  gsaoiKeywordReader:  GsaoiKeywordReader[F],
  altairKeywordReader: AltairKeywordReader[F],
  gemsKeywordsReader:  GemsKeywordReader[F],
  gwsKeywordReader:    GwsKeywordReader[F]
)

object Systems {

  final case class Builder(
    settings:   SeqexecEngineConfiguration,
    service:    CaService,
    tops:       Map[String, String]
  )(implicit L: Logger[IO], T: Timer[IO]) {
    def odbProxy[F[_]: Sync: Logger]: OdbProxy[F] = OdbProxy[F](
      new Peer(settings.odb.renderString, 8443, null),
      if (settings.odbNotifications)
        OdbProxy.OdbCommandsImpl[F](new Peer(settings.odb.renderString, 8442, null))
      else new OdbProxy.DummyOdbCommands[F]
    )

    def dhs[F[_]: Concurrent: Timer: Logger](httpClient: Client[F]): F[DhsClient[F]] =
      if (settings.systemControl.dhs.command)
        DhsClientHttp[F](httpClient, settings.dhsServer).pure[F]
      else
        DhsClientSim.apply[F]

    // TODO make instruments controllers generalized on F
    def gcal: IO[(GcalController[IO], GcalKeywordReader[IO])] =
      if (settings.systemControl.gcal.realKeywords)
        GcalEpics
          .instance[IO](service, tops)
          .map(epicsSys =>
            (
              if (settings.systemControl.gcal.command) GcalControllerEpics(epicsSys)
              else GcalControllerSim[IO],
              GcalKeywordsReaderEpics(epicsSys)
            )
          )
      else (GcalControllerSim[IO], DummyGcalKeywordsReader[IO]).pure[IO]

    def tcsSouth(
      tcsEpicsO: => Option[TcsEpics[IO]],
      site:      Site,
      gcdb:      GuideConfigDb[IO]
    ): TcsSouthController[IO] =
      tcsEpicsO
        .map { tcsEpics =>
          if (settings.systemControl.tcs.command && site === Site.GS)
            TcsSouthControllerEpics(tcsEpics, gcdb)
          else TcsSouthControllerSim[IO]
        }
        .getOrElse(TcsSouthControllerSim[IO])

    def tcsNorth(tcsEpicsO: => Option[TcsEpics[IO]], site: Site): TcsNorthController[IO] =
      tcsEpicsO
        .map { tcsEpics =>
          if (settings.systemControl.tcs.command && site === Site.GN)
            TcsNorthControllerEpics(tcsEpics)
          else TcsNorthControllerSim[IO]
        }
        .getOrElse(TcsNorthControllerSim[IO])

    def altair(
      tcsEpicsO: => Option[TcsEpics[IO]]
    ): IO[(AltairController[IO], AltairKeywordReader[IO])] =
      if (settings.systemControl.altair.realKeywords)
        AltairEpics.instance[IO](service, tops).map { altairEpics =>
          tcsEpicsO
            .map { tcsEpics =>
              if (settings.systemControl.altair.command && settings.systemControl.tcs.command)
                AltairControllerEpics.apply(altairEpics, tcsEpics)
              else
                AltairControllerSim[IO]
            }
            .map((_, AltairKeywordReaderEpics(altairEpics)))
            .getOrElse((AltairControllerSim[IO], AltairKeywordReaderEpics(altairEpics)))
        }
      else
        (AltairControllerSim[IO], AltairKeywordReaderDummy[IO]).pure[IO]

    def tcsObjects(gcdb: GuideConfigDb[IO], site: Site): IO[
      (
        TcsNorthController[IO],
        TcsSouthController[IO],
        TcsKeywordsReader[IO],
        AltairController[IO],
        AltairKeywordReader[IO]
      )
    ] =
      for {
        tcsEpicsO             <- settings.systemControl.tcs.realKeywords
                                   .option(TcsEpics.instance[IO](service, tops))
                                   .sequence
        (altairCtr, altairKR) <- altair(tcsEpicsO)
        tcsNCtr                = tcsNorth(tcsEpicsO, site)
        tcsSCtr                = tcsSouth(tcsEpicsO, site, gcdb)
        tcsKR                  = tcsEpicsO.map(TcsKeywordsReaderEpics[IO]).getOrElse(DummyTcsKeywordsReader[IO])
      } yield (
        tcsNCtr,
        tcsSCtr,
        tcsKR,
        altairCtr,
        altairKR
      )

    def gems(
      gsaoiController: GsaoiGuider[IO],
      gsaoiEpicsO:     => Option[GsaoiEpics[IO]]
    ): IO[(GemsController[IO], GemsKeywordReader[IO])] =
      if (settings.systemControl.gems.realKeywords)
        GemsEpics.instance[IO](service, tops).map { gemsEpics =>
          gsaoiEpicsO
            .map { gsaoiEpics =>
              (
                if (settings.systemControl.gems.command && settings.systemControl.tcs.command)
                  GemsControllerEpics(gemsEpics, gsaoiController)
                else
                  GemsControllerSim[IO],
                GemsKeywordReaderEpics[IO](gemsEpics, gsaoiEpics)
              )
            }
            .getOrElse(
              (GemsControllerEpics(gemsEpics, gsaoiController), GemsKeywordReaderDummy[IO])
            )
        }
      else (GemsControllerSim[IO], GemsKeywordReaderDummy[IO]).pure[IO]

    def gsaoi(
      gsaoiEpicsO: => Option[GsaoiEpics[IO]]
    ): IO[(GsaoiFullHandler[IO], GsaoiKeywordReader[IO])] =
      gsaoiEpicsO
        .map { gsaoiEpics =>
          (
            if (settings.systemControl.gsaoi.command) GsaoiControllerEpics(gsaoiEpics).pure[IO]
            else GsaoiControllerSim[IO]
          ).map((_, GsaoiKeywordReaderEpics(gsaoiEpics)))
        }
        .getOrElse(GsaoiControllerSim[IO].map((_, GsaoiKeywordReaderDummy[IO])))

    def gemsObjects: IO[
      (GemsController[IO], GemsKeywordReader[IO], GsaoiController[IO], GsaoiKeywordReader[IO])
    ] =
      for {
        gsaoiEpicsO         <- settings.systemControl.gsaoi.realKeywords
                                 .option(GsaoiEpics.instance[IO](service, tops))
                                 .sequence
        (gsaoiCtr, gsaoiKR) <- gsaoi(gsaoiEpicsO)
        (gemsCtr, gemsKR)   <- gems(gsaoiCtr, gsaoiEpicsO)
      } yield (gemsCtr, gemsKR, gsaoiCtr, gsaoiKR)

    /*
     * Type parameters are
     * E: Instrument EPICS class
     * C: Instrument controller class
     * K: Instrument keyword reader class
     */
    def instObjects[F[_]: Monad, E, C, K](
      ctrl:                 ControlStrategy,
      epicsBuilder:         (CaService, Map[String, String]) => F[E],
      realCtrlBuilder:      (=> E) => C,
      simCtrlBuilder:       => F[C],
      realKeyReaderBuilder: E => K,
      simKeyReaderBuilder:  => K
    ): F[(C, K)] =
      if (ctrl.realKeywords)
        epicsBuilder(service, tops).flatMap(epicsSys =>
          (
            if (ctrl.command) realCtrlBuilder(epicsSys).pure[F]
            else simCtrlBuilder
          ).map((_, realKeyReaderBuilder(epicsSys)))
        )
      else
        simCtrlBuilder.map((_, simKeyReaderBuilder))

    def gnirs: IO[(GnirsController[IO], GnirsKeywordReader[IO])] =
      instObjects(
        settings.systemControl.gnirs,
        GnirsEpics.instance[IO],
        GnirsControllerEpics.apply[IO],
        GnirsControllerSim.apply[IO],
        GnirsKeywordReaderEpics[IO],
        GnirsKeywordReaderDummy[IO]
      )

    def niri: IO[(NiriController[IO], NiriKeywordReader[IO])] =
      instObjects(
        settings.systemControl.niri,
        NiriEpics.instance[IO],
        NiriControllerEpics.apply[IO],
        NiriControllerSim.apply[IO],
        NiriKeywordReaderEpics[IO],
        NiriKeywordReaderDummy[IO]
      )

    def nifs: IO[(NifsController[IO], NifsKeywordReader[IO])] =
      instObjects(
        settings.systemControl.nifs,
        NifsEpics.instance[IO],
        NifsControllerEpics.apply[IO],
        NifsControllerSim.apply[IO],
        NifsKeywordReaderEpics[IO],
        NifsKeywordReaderDummy[IO]
      )

    def gmosSouth(gmosEpicsO: Option[GmosEpics[IO]], site: Site): IO[GmosSouthController[IO]] =
      gmosEpicsO
        .filter(_ => settings.systemControl.gmos.command && site === Site.GS)
        .map(GmosSouthControllerEpics.apply[IO](_).pure[IO])
        .getOrElse(GmosControllerSim.south[IO])

    def gmosNorth(gmosEpicsO: Option[GmosEpics[IO]], site: Site): IO[GmosNorthController[IO]] =
      gmosEpicsO
        .filter(_ => settings.systemControl.gmos.command && site === Site.GN)
        .map(GmosNorthControllerEpics.apply[IO](_).pure[IO])
        .getOrElse(GmosControllerSim.north[IO])

    def gmosObjects(
      site: Site
    ): IO[(GmosSouthController[IO], GmosNorthController[IO], GmosKeywordReader[IO])] =
      for {
        gmosEpicsO   <- settings.systemControl.gmos.realKeywords
                          .option(GmosEpics.instance[IO](service, tops))
                          .sequence
        gmosSouthCtr <- gmosSouth(gmosEpicsO, site)
        gmosNorthCtr <- gmosNorth(gmosEpicsO, site)
        gmosKR        = gmosEpicsO.map(GmosKeywordReaderEpics[IO]).getOrElse(GmosKeywordReaderDummy[IO])
      } yield (gmosSouthCtr, gmosNorthCtr, gmosKR)

    def flamingos2: IO[Flamingos2Controller[IO]] =
      if (settings.systemControl.f2.command)
        Flamingos2Epics.instance[IO](service, tops).map(Flamingos2ControllerEpics(_))
      else if (settings.instForceError) Flamingos2ControllerSimBad[IO](settings.failAt)
      else Flamingos2ControllerSim[IO]

    def gpi[F[_]: ConcurrentEffect: Timer: Logger](
      httpClient: Client[F]
    ): Resource[F, GpiController[F]] = {
      def gpiClient: Resource[F, GpiClient[F]] =
        if (settings.systemControl.gpi.command)
          GpiClient.gpiClient[F](settings.gpiUrl.renderString, GpiStatusApply.statusesToMonitor)
        else GpiClient.simulatedGpiClient[F]

      def gpiGDS(httpClient: Client[F]): Resource[F, GdsClient[F]] =
        Resource.pure[F, GdsClient[F]](
          GdsHttpClient(if (settings.systemControl.gpiGds.command) httpClient
                        else GdsHttpClient.alwaysOkClient[F],
                        settings.gpiGDS
          )
        )

      (gpiClient, gpiGDS(httpClient)).mapN(GpiController(_, _))
    }

    def ghost[F[_]: ConcurrentEffect: Timer: Logger](
      httpClient: Client[F]
    ): Resource[F, GhostController[F]] = {
      def ghostClient: Resource[F, GhostClient[F]] =
        if (settings.systemControl.ghost.command)
          GhostClient.ghostClient[F](settings.ghostUrl.renderString)
        else GhostClient.simulatedGhostClient

      def ghostGDS(httpClient: Client[F]): Resource[F, GdsClient[F]] =
        Resource.pure[F, GdsClient[F]](
          GdsXmlrpcClient(if (settings.systemControl.ghostGds.command) httpClient
                          else GdsXmlrpcClient.alwaysOkClient[F],
                          settings.ghostGDS
          )
        )

      (ghostClient, ghostGDS(httpClient)).mapN(GhostController(_, _))
    }

    def gws: IO[GwsKeywordReader[IO]] =
      if (settings.systemControl.gws.realKeywords)
        GwsEpics.instance[IO](service, tops).map(GwsKeywordsReaderEpics[IO])
      else DummyGwsKeywordsReader[IO].pure[IO]

    def build(site: Site, httpClient: Client[IO])(implicit
      C:            ContextShift[IO]
    ): Resource[IO, Systems[IO]] =
      for {
        odbProxy                                   <- Resource.pure[IO, OdbProxy[IO]](odbProxy[IO])
        dhsClient                                  <- Resource.eval(dhs[IO](httpClient))
        gcdb                                       <- Resource.eval(GuideConfigDb.newDb[IO])
        (gcalCtr, gcalKR)                          <- Resource.eval(gcal)
        (tcsGN, tcsGS, tcsKR, altairCtr, altairKR) <- Resource.eval(tcsObjects(gcdb, site))
        (gemsCtr, gemsKR, gsaoiCtr, gsaoiKR)       <- Resource.eval(gemsObjects)
        (gnirsCtr, gnirsKR)                        <- Resource.eval(gnirs)
        f2Controller                               <- Resource.eval(flamingos2)
        (niriCtr, niriKR)                          <- Resource.eval(niri)
        (nifsCtr, nifsKR)                          <- Resource.eval(nifs)
        (gmosSouthCtr, gmosNorthCtr, gmosKR)       <- Resource.eval(gmosObjects(site))
        gpiController                              <- gpi[IO](httpClient)
        ghostController                            <- ghost[IO](httpClient)
        gwsKR                                      <- Resource.eval(gws)
      } yield Systems[IO](
        odbProxy,
        dhsClient,
        tcsGS,
        tcsGN,
        gcalCtr,
        f2Controller,
        gmosSouthCtr,
        gmosNorthCtr,
        gnirsCtr,
        gsaoiCtr,
        gpiController,
        ghostController,
        niriCtr,
        nifsCtr,
        altairCtr,
        gemsCtr,
        gcdb,
        tcsKR,
        gcalKR,
        gmosKR,
        gnirsKR,
        niriKR,
        nifsKR,
        gsaoiKR,
        altairKR,
        gemsKR,
        gwsKR
      )
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("=|,")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

  def build(
    site:       Site,
    httpClient: Client[IO],
    settings:   SeqexecEngineConfiguration,
    service:    CaService
  )(implicit T: Timer[IO], L: Logger[IO], C: ContextShift[IO]): Resource[IO, Systems[IO]] =
    Builder(settings, service, decodeTops(settings.tops)).build(site, httpClient)
}
