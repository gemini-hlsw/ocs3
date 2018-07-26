// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package giapi

import cats._
import cats.effect._
import cats.implicits._
import edu.gemini.aspen.giapi.commands.HandlerResponse.Response
import edu.gemini.aspen.giapi.status.{StatusHandler, StatusItem}
import edu.gemini.aspen.giapi.statusservice.{StatusHandlerAggregate, StatusService}
import edu.gemini.aspen.giapi.util.jms.status.StatusGetter
import edu.gemini.aspen.gmp.commands.jms.client.CommandSenderClient
import edu.gemini.jms.activemq.provider.ActiveMQJmsProvider
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import shapeless.Typeable._
import fs2.{Stream, async}
import giapi.client.commands.{Command, Completed}

package object client {

  /**
    * Allowed types according to GIAPI
    */
  implicit val strItemGetter: ItemGetter[String] = new ItemGetter[String] {}

  implicit val doubleItemGetter: ItemGetter[Double] = new ItemGetter[Double] {}

  implicit val intItemGetter: ItemGetter[Int] = new ItemGetter[Int] {}

  implicit val floatItemGetter: ItemGetter[Float] = new ItemGetter[Float] {}
}

package client {

  import giapi.client.commands.CommandResult

  final case class GiapiException(str: String) extends RuntimeException(str)

  /**
    * Typeclass to present as evidence when calling `Giapi.get`
    */
  sealed abstract class ItemGetter[A: shapeless.Typeable] {

    /**
      * Attempt to convert any value to A as sent by StatusHandler
      */
    def value(p: Any): Option[A] = shapeless.Typeable[A].cast(p)
  }

  object ItemGetter {

    @inline
    def apply[F](implicit instance: ItemGetter[F]): ItemGetter[F] = instance
  }

  /**
    * Represents a connection to a GIAPi based instrument
    *
    * @tparam F Effect type
    */
  trait GiapiConnection[F[_]] {
    def connect: F[Giapi[F]]
  }

  /**
    * Algebra to interact with a GIAPI instrument
    *
    * @tparam F Effect Type
    */
  trait Giapi[F[_]] {

    /**
      * Returns a value for the status item. If not found or there is an error, an exception could be thrown
      */
    def get[A: ItemGetter](statusItem: String): F[A]

    /**
      * Attempts to read a value. If not found an empty F is returned
      */
    def getO[A: ItemGetter](statusItem: String): F[Option[A]]

    /**
      * Executes a command as defined on GIAPI
      * Note that commands can end in ERROR or COMPLETED
      * Giapi has an extra case where we have a command ACCEPTED and it will complete in the future
      * That makes handling easier with callbacks on Java land but on IO-land it makes more sense to
      * wait for ERROR/COMPLETED and do async calls avobe this level
      *
      * This decision may be review in the future
      */
    def command(command: Command): F[CommandResult]

    /**
      * Returns a stream of values for the status item.
      */
    def stream[A: ItemGetter](statusItem: String, ec: ExecutionContext): F[Stream[F, A]]

    /**
      * Close the connection
      */
    def close: F[Unit]
  }

  /**
    * Interpreters
    */
  object Giapi {
    private final case class StatusStreamer(aggregate: StatusHandlerAggregate, ss: StatusService)

    private def statusGetter[F[_]: Sync](c: ActiveMQJmsProvider): F[StatusGetter] = Sync[F].delay {
      val sg = new StatusGetter("statusGetter")
      sg.startJms(c)
      sg
    }

    private def commandSenderClient[F[_]: Applicative](c: ActiveMQJmsProvider): F[CommandSenderClient] =
      Applicative[F].pure {
        new CommandSenderClient(c)
      }

    private def statusStreamer[F[_]: Sync](c: ActiveMQJmsProvider): F[StatusStreamer] = Sync[F].delay {
      val aggregate     = new StatusHandlerAggregate()
      val statusService = new StatusService(aggregate, "statusService", "*")
      statusService.startJms(c)
      StatusStreamer(aggregate, statusService)
    }

    private def streamItem[F[_]: Effect, A: ItemGetter](agg: StatusHandlerAggregate, statusItem: String,
                                                         ec: ExecutionContext): F[Stream[F, A]] =
      Sync[F].delay {
        implicit val exc: ExecutionContext = ec
        def statusHandler(q: async.mutable.Queue[F, A]) = new StatusHandler {

          override def update[B](item: StatusItem[B]): Unit =
            // Check the item name and attempt convert it to A
            if (item.getName === statusItem) {
              ItemGetter[A].value(item.getValue).foreach { a =>
                async.unsafeRunAsync(q.enqueue1(a))(_ => IO.unit)
              }
            }

          override def getName: String = "StatusHandler"
        }

        // Put the items in a queue as they arrive to the stream
        for {
          q <- Stream.eval(async.unboundedQueue[F, A])
          i <- Stream.bracket {
            Effect[F].delay {
              val sh = statusHandler(q)
              agg.bindStatusHandler(sh)
              sh
            }
          }(_ => q.dequeue, sh => Effect[F].delay(agg.unbindStatusHandler(sh)))
        } yield i
      }

    /**
      * Interpreter on F
      *
      * @param url Url to connect to
      * @tparam F Effect type
      */
    // scalastyle:off
    def giapiConnection[F[_]: Effect](url: String, commandsTimeout: Duration): GiapiConnection[F] =
      new GiapiConnection[F] {
        private def giapi(c: ActiveMQJmsProvider,
                          sg: StatusGetter,
                          cc: CommandSenderClient,
                          ss: StatusStreamer) =
          new Giapi[F] {
            @SuppressWarnings(Array("org.wartremover.warts.Throw"))
            override def get[A: ItemGetter](statusItem: String): F[A] =
              Sync[F].delay {
                val item = sg.getStatusItem[A](statusItem)
                // Note item.getValue can throw if e.g. the item is unknown, let's avoid NPEs though
                Option(item) match {
                  case Some(i) => i.getValue
                  case None    => throw GiapiException(s"Status item $statusItem not found")
                }
              }

            def getO[A: ItemGetter](statusItem: String): F[Option[A]] =
              Sync[F].delay {
                val item = sg.getStatusItem[A](statusItem)
                Option(item).map(_.getValue)
              }

            override def command(command: Command): F[CommandResult] =
              commands.sendCommand(cc, command, commandsTimeout)

            override def stream[A: ItemGetter](statusItem: String,
                                               ec: ExecutionContext): F[Stream[F, A]] =
              streamItem[F, A](ss.aggregate, statusItem, ec)

            override def close: F[Unit] =
              for {
                _ <- Sync[F].delay(sg.stopJms())
                _ <- Sync[F].delay(ss.ss.stopJms())
                _ <- Sync[F].delay(c.stopConnection())
              } yield ()

          }

        private def build(ref: async.Ref[F, ActiveMQJmsProvider]): F[Giapi[F]] =
          for {
            c  <- ref.get
            sg <- statusGetter[F](c)
            cc <- commandSenderClient[F](c)
            ss <- statusStreamer[F](c)
          } yield giapi(c, sg, cc, ss)

        def connect: F[Giapi[F]] =
          for {
            c   <- Sync[F].delay(new ActiveMQJmsProvider(url)) // Build the connection
            ref <- async.refOf(c)                              // store a reference
            _   <- Sync[F].delay(c.startConnection())          // Start the connection
            c   <- build(ref)                                  // Build the interpreter
          } yield c
      }
      // scalastyle:on

    /**
      * Interpreter on Id
      */
    def giapiConnectionId: GiapiConnection[Id] = new GiapiConnection[Id] {
      override def connect: Id[Giapi[Id]] = new Giapi[Id] {
        override def get[A: ItemGetter](statusItem: String): Id[A] = sys.error(s"Cannot read $statusItem")
        override def getO[A: ItemGetter](statusItem: String): Id[Option[A]] = None
        override def stream[A: ItemGetter](statusItem: String, ec: ExecutionContext): Id[Stream[Id, A]] = Stream.raiseError(new RuntimeException(s"Cannot read $statusItem"))
        override def command(command: Command): Id[CommandResult] = Completed(Response.COMPLETED)
        override def close: Id[Unit] = ()
      }
    }

    /**
      * Simulator interpreter on IO, Reading items will fail and all commands will succeed
      */
    def giapiConnectionIO: GiapiConnection[IO] = new GiapiConnection[IO] {
      override def connect: IO[Giapi[IO]] = IO.pure(new Giapi[IO] {
        override def get[A: ItemGetter](statusItem: String): IO[A] = IO.raiseError(new RuntimeException(s"Cannot read $statusItem"))
        override def getO[A: ItemGetter](statusItem: String): IO[Option[A]] = IO.pure(None)
        override def stream[A: ItemGetter](statusItem: String, ec: ExecutionContext): IO[Stream[IO, A]] = IO.pure(Stream.empty.covary[IO])
        override def command(command: Command): IO[CommandResult] = IO.pure(Completed(Response.COMPLETED))
        override def close: IO[Unit] = IO.unit
      })
    }
  }

}
