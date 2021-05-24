// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.keywords

import scala.concurrent.duration._

import cats.effect.Concurrent
import cats.effect.Sync
import cats.effect.Timer
import cats.syntax.all._
import io.circe.{ Encoder, Json }
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.io._
import org.http4s.implicits._
import seqexec.model.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqexecFailure

/**
 * Gemini Data service client
 */
trait GdsClient[F[_]] extends Http4sClientDsl[F] {

  /**
   * Set the keywords for an image
   */
  def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit]

  def openObservation(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): F[Unit]

  def closeObservation(id: ImageFileId): F[Unit]
}

object GdsClient {

  def apply[F[_]: Concurrent](base: Client[F], gdsUri: Uri)(implicit
    timer:                          Timer[F]
  ): GdsClient[F] = new GdsClient[F] {

    private val client = {
      val max             = 2
      var attemptsCounter = 1
      val policy          = RetryPolicy[F] { attempts: Int =>
        if (attempts >= max) None
        else {
          attemptsCounter = attemptsCounter + 1
          Some(10.milliseconds)
        }
      }
      Retry(policy)(base)
    }

    /**
     * Set the keywords for an image
     */
    override def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit] =
      makeRequest("keywords", KeywordRequest(id, ks).asJson)

    override def openObservation(
      obsId: Observation.Id,
      id:    ImageFileId,
      ks:    KeywordBag
    ): F[Unit] =
      makeRequest("open-observation", OpenObservationRequest(obsId, id, ks).asJson)

    override def closeObservation(id: ImageFileId): F[Unit] =
      makeRequest("close-observation", CloseObservationRequest(id).asJson)

    private def makeRequest(path: String, body: Json): F[Unit] = {
      val uri         = gdsUri / path
      val postRequest = POST(body, uri)

      // Do the request
      client
        .expect[String](postRequest)
        .adaptErr { case e => SeqexecFailure.GdsException(e, uri) }
        .void
    }
  }

  case class KeywordRequest(id: String, ks: KeywordBag)
  case class OpenObservationRequest(obsId: Observation.Id, id: String, ks: KeywordBag)
  case class CloseObservationRequest(id: String)

  implicit val ikwEncoder: Encoder[InternalKeyword] =
    Encoder.forProduct3("keyword", "value_type", "value")(ikw =>
      (ikw.name.name, KeywordType.gdsKeywordType(ikw.keywordType), ikw.value)
    )

  implicit val kwrEncoder: Encoder[KeywordRequest] =
    Encoder.forProduct2("data_label", "keywords")(kwr => (kwr.id, kwr.ks.keywords))

  implicit val oorEncoder: Encoder[OpenObservationRequest] =
    Encoder.forProduct3("program_id", "data_label", "keywords")(oor =>
      (oor.obsId.format, oor.id, oor.ks.keywords)
    )

  implicit val corEncoder: Encoder[CloseObservationRequest] =
    Encoder.forProduct1("data_label")(cor => cor.id)

  /**
   * Client for testing always returns ok
   */
  def alwaysOkClient[F[_]: Sync]: Client[F] = {
    val service = HttpRoutes.of[F] { case _ =>
      Response[F](Status.Ok).withEntity("Success").pure[F]
    }
    Client.fromHttpApp(service.orNotFound)
  }
}
