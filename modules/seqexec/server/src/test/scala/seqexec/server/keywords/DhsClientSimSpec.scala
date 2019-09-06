// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.keywords

import cats.effect.IO
import gem.enum.KeywordName
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import java.time.LocalDate
import org.scalatest.{FlatSpec, Matchers}
import seqexec.server.keywords.DhsClient.Permanent

class DhsClientSimSpec extends FlatSpec with Matchers {
  private implicit def unsafeLogger = Slf4jLogger.unsafeCreate[IO]

  "DhsClientSim" should "produce data labels for today" in {
      DhsClientSim.unsafeApply[IO](LocalDate.of(2016, 4, 15)).createImage(DhsClient.ImageParameters(Permanent, Nil)).unsafeRunSync() should matchPattern {
        case "S20160415S0001" =>
      }
    }
  it should "accept keywords" in {
    val client = DhsClientSim.unsafeApply[IO](LocalDate.of(2016, 4, 15))
    client.createImage(DhsClient.ImageParameters(Permanent, Nil)).flatMap { id =>
      client.setKeywords(id, KeywordBag(Int32Keyword(KeywordName.TELESCOP, 10)), finalFlag = true)
    }.unsafeRunSync() shouldEqual (())
  }
}
