// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.main

import cats.effect._
import gem.dao.DatabaseConfiguration
import gem.web.WebServer
import gem.telnetd.TelnetServer
import org.flywaydb.core.Flyway

object MainServer {

  // Run flyway migrations
  private def migrate[F[_]: Sync](db: DatabaseConfiguration): F[Int] =
    Sync[F].delay {
      val flyway = Flyway.configure.dataSource(db.connectUrl, db.userName, db.password).load
      flyway.migrate()
    }

  /** A single-element stream that starts the server up and shuts it down on exit. */
  def resource[F[_]: ConcurrentEffect: ContextShift: Timer](cfg: MainConfiguration)(
    implicit ev: ContextShift[IO]
  ): Resource[F, Unit] =
    for {
      _   <- Resource.liftF(migrate(cfg.database))
      _   <- TelnetServer.server(cfg.database, cfg.telnetd)
      _   <- WebServer.resource[F](cfg.web, cfg.database)
    } yield ()

}
