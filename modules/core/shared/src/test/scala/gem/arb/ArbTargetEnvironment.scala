// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package arb

import gem.enum.Instrument
import gem.syntax.treesetcompanion._

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._
import scala.collection.immutable.TreeSet

trait ArbTargetEnvironment {
  import ArbAsterism._
  import ArbEnumerated._
  import ArbUserTarget._

  // TODO: GuideEnvironment
  def genTargetEnvironment(i: Instrument): Gen[TargetEnvironment] =
    for {
      a <- frequency((9, genAsterism(i).map(Option(_))), (1, const(Option.empty[Asterism])))
      n <- choose(0, 10)
      u <- listOfN(n, arbitrary[UserTarget]).map(us => TreeSet.fromList(us))
    } yield a.fold(TargetEnvironment.fromInstrument(i, None, u))(TargetEnvironment.fromAsterism(_, None, u))

  implicit val arbTargetEnvironment: Arbitrary[TargetEnvironment] =
    Arbitrary {
      for {
        i <- arbitrary[Instrument]
        e <- genTargetEnvironment(i)
      } yield e
    }

  // TODO: GuideEnvironment
  implicit val cogTargetEnvironment: Cogen[TargetEnvironment] =
    Cogen[(Option[Asterism], List[UserTarget])].contramap(e => (e.asterism, e.userTargets.toList))

}

object ArbTargetEnvironment extends ArbTargetEnvironment