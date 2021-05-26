// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.keywords

import scala.xml.Elem

import cats.effect.Concurrent
import cats.effect.Sync
import cats.effect.Timer
import cats.syntax.all._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.scalaxml._
import seqexec.model.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqexecFailure

object GdsXmlrpcClient {

  def apply[F[_]: Concurrent](base: Client[F], gdsUri: Uri)(implicit
    timer:                          Timer[F]
  ): GdsClient[F] = new GdsClient[F] {

    private val client = makeClient(base)

    // Build an xml rpc request to store keywords
    private def storeKeywords(id: ImageFileId, ks: KeywordBag): Elem =
      <methodCall>
        <methodName>HeaderReceiver.storeKeywords</methodName>
        <params>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
          {keywordsParam(ks)}
        </params>
      </methodCall>

    /**
     * Set the keywords for an image
     */
    override def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit] = {
      // Build the request
      val xmlRpc      = storeKeywords(id, ks)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(scalaxml.xml)
        .map(GdsXmlrpcClient.parseError)
        .ensureOr(toSeqexecFailure)(_.isRight)
        .void
    }

    // Build an xml rpc request to open an observation
    private def openObservationRPC(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): Elem =
      <methodCall>
        <methodName>HeaderReceiver.openObservation</methodName>
        <params>
          <param>
            <value>
              <string>{obsId.format}</string>
            </value>
          </param>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
          {keywordsParam(ks)}
        </params>
      </methodCall>

    override def openObservation(
      obsId: Observation.Id,
      id:    ImageFileId,
      ks:    KeywordBag
    ): F[Unit] = {
      // Build the request
      val xmlRpc      = openObservationRPC(obsId, id, ks)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(scalaxml.xml)
        .map(GdsXmlrpcClient.parseError)
        .ensureOr(toSeqexecFailure)(_.isRight)
        .void
    }

    // Build an xml rpc request to close an observation
    private def closeObservationRPC(id: ImageFileId): Elem =
      <methodCall>
        <methodName>HeaderReceiver.closeObservation</methodName>
        <params>
          <param>
            <value>
              <string>{id}</string>
            </value>
          </param>
        </params>
      </methodCall>

    override def closeObservation(id: ImageFileId): F[Unit] = {
      // Build the request
      val xmlRpc      = closeObservationRPC(id)
      val postRequest = POST(xmlRpc, gdsUri)

      // Do the request
      client
        .expect[Elem](postRequest)(scalaxml.xml)
        .map(GdsXmlrpcClient.parseError)
        .ensureOr(toSeqexecFailure)(_.isRight)
        .void
    }

    override def abortObservation(id: ImageFileId): F[Unit] =
      implicitly[Concurrent[F]].unit

    private def keywordsParam(ks: KeywordBag): Elem =
      <param>
        <value>
          <array>
            <data>
              {
        ks.keywords.map { k =>
          <value><string>{
            s"${k.name},${KeywordType.gdsKeywordType(k.keywordType)},${k.value}"
          }</string></value>
        }
      }
            </data>
          </array>
        </value>
      </param>

    def toSeqexecFailure(v: Either[String, Elem]): SeqexecFailure =
      SeqexecFailure.GdsXmlError(v.left.getOrElse(""), gdsUri)

  }

  def parseError(e: Elem): Either[String, Elem] = {
    val v = for {
      m <- e \\ "methodResponse" \ "fault" \ "value" \ "struct" \\ "member"
      if (m \ "name").text === "faultString"
    } yield (m \ "value").text.trim
    v.headOption.toLeft(e)
  }

  /**
   * Client for testing always returns ok
   */
  def alwaysOkClient[F[_]: Sync]: Client[F] = {
    val service = HttpRoutes.of[F] { case _ =>
      val response =
        <methodResponse>
            <params>
              <param>
                  <value><string>Ok</string></value>
              </param>
            </params>
          </methodResponse>
      Response[F](Status.Ok).withEntity(response).pure[F]
    }
    Client.fromHttpApp(service.orNotFound)
  }
}
