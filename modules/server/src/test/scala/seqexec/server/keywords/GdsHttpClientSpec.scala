// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.keywords

import cats.effect._
import cats.tests.CatsSuite
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import io.circe.syntax._
import java.time.Year
import lucuma.core.enum.{ Half, KeywordName, ProgramType, Site }
import lucuma.core.math.Index
import lucuma.core.model.Semester
import seqexec.model.dhs._
import seqexec.model.{ Observation, ProgramId }
import seqexec.server.TestCommon._
import seqexec.server.SeqexecFailure

import GdsHttpClient._

final class GdsHttpClientSpec extends CatsSuite {
  val uri: Uri    = Uri.unsafeFromString("http://localhost:8888")
  val badStatuses = List(Status.BadRequest, Status.NotFound, Status.RequestTimeout)

  test("openObservation should succeed if http client returns Status.Ok") {
    val client = GdsHttpClient(httpClient(Status.Ok, "Success"), uri)
    val progId =
      ProgramId.Science(Site.GN, Semester(Year.of(2021), Half.A), ProgramType.CAL, Index.One)
    val obsId  = Observation.Id(progId, Index.One)
    val id     = toImageFileId("label")
    val ks     =
      KeywordBag(BooleanKeyword(KeywordName.SSA, false), DoubleKeyword(KeywordName.OBJECT, 98.76))
    client.openObservation(obsId, id, ks).attempt.unsafeRunSync().isRight shouldEqual true
  }

  test("openObservation should throw exception if http client returns a bad status") {
    val progId =
      ProgramId.Science(Site.GN, Semester(Year.of(2021), Half.A), ProgramType.CAL, Index.One)
    val obsId  = Observation.Id(progId, Index.One)
    val id     = toImageFileId("label")
    val ks     =
      KeywordBag(BooleanKeyword(KeywordName.SSA, false), DoubleKeyword(KeywordName.OBJECT, 98.76))
    badStatuses.foreach { status =>
      val client = GdsHttpClient(httpClient(status, "Error"), uri)
      assertThrows[SeqexecFailure.GdsException](
        client.openObservation(obsId, id, ks).unsafeRunSync()
      )
    }
  }

  test("closeObservation should succeed if http client returns Status.Ok") {
    val client = GdsHttpClient(httpClient(Status.Ok, "Success"), uri)
    val id     = toImageFileId("IFI")
    client.closeObservation(id).attempt.unsafeRunSync().isRight shouldEqual true
  }

  test("closeObservation should throw exception if http client returns a bad status") {
    val id = toImageFileId("ID")
    badStatuses.foreach { status =>
      val client = GdsHttpClient(httpClient(status, "Error"), uri)
      assertThrows[SeqexecFailure.GdsException](client.closeObservation(id).unsafeRunSync())
    }
  }

  test("setKeywords should succeed if http client returns Status.Ok") {
    val client = GdsHttpClient(httpClient(Status.Ok, "Success"), uri)
    val id     = toImageFileId("IFI")
    val ks     = KeywordBag(StringKeyword(KeywordName.INSTRUMENT, "The INSTR."),
                        Int32Keyword(KeywordName.OBJECT, 123)
    )
    client.setKeywords(id, ks).attempt.unsafeRunSync().isRight shouldEqual true
  }

  test("setKeywords should throw exception if http client returns a bad status") {
    val id = toImageFileId("ID")
    val ks = KeywordBag(StringKeyword(KeywordName.INSTRUMENT, "The INSTR."),
                        Int32Keyword(KeywordName.OBJECT, 123)
    )
    badStatuses.foreach { status =>
      val client = GdsHttpClient(httpClient(status, "Error"), uri)
      assertThrows[SeqexecFailure.GdsException](client.setKeywords(id, ks).unsafeRunSync())
    }
  }

  test("OpenObservationRequests should encode to JSON properly") {
    val progId: ProgramId =
      ProgramId.Science(Site.GN, Semester(Year.of(2021), Half.A), ProgramType.CAL, Index.One)
    val obsId             = Observation.Id(progId, Index.One)
    val id: ImageFileId   = toImageFileId("label")
    val ks                =
      KeywordBag(BooleanKeyword(KeywordName.SSA, false), DoubleKeyword(KeywordName.OBJECT, 98.76))

    val json = OpenObservationRequest(obsId, id, ks).asJson.toString
    json shouldEqual openObsExpectedJson
  }

  test("CloseObservationRequests should encode to JSON properly") {
    val id: ImageFileId = toImageFileId("DataLabel")

    val json = CloseObservationRequest(id).asJson.toString
    json shouldEqual closeObsExpectedJson
  }

  test("KeywordRequests should encode to JSON properly") {
    val id: ImageFileId = toImageFileId("IFI")
    val ks              = KeywordBag(StringKeyword(KeywordName.INSTRUMENT, "The INSTR."),
                        Int32Keyword(KeywordName.OBJECT, 123)
    )

    val json = KeywordRequest(id, ks).asJson.toString
    json shouldEqual keywordExpectedJson
  }

  def httpClient(status: Status, body: String): Client[IO] = {
    val service = HttpRoutes.of[IO] { case _ =>
      Response[IO](status).withEntity(body).pure[IO]
    }
    Client.fromHttpApp(service.orNotFound)
  }

  val openObsExpectedJson = """{
    |  "program_id" : "GN-2021A-CAL-1-1",
    |  "data_label" : "label",
    |  "keywords" : [
    |    {
    |      "keyword" : "SSA",
    |      "value_type" : "BOOLEAN",
    |      "value" : "false"
    |    },
    |    {
    |      "keyword" : "OBJECT",
    |      "value_type" : "DOUBLE",
    |      "value" : "98.76"
    |    }
    |  ]
    |}""".stripMargin

  val closeObsExpectedJson = """{
    |  "data_label" : "DataLabel"
    |}""".stripMargin

  val keywordExpectedJson = """{
    |  "data_label" : "IFI",
    |  "keywords" : [
    |    {
    |      "keyword" : "INSTRUME",
    |      "value_type" : "STRING",
    |      "value" : "The INSTR."
    |    },
    |    {
    |      "keyword" : "OBJECT",
    |      "value_type" : "INT",
    |      "value" : "123"
    |    }
    |  ]
    |}""".stripMargin
}
