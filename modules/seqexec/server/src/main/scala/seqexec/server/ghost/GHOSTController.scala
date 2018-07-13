// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.ghost


import cats.implicits._
import cats.{Show, Eq}
import cats.effect.Sync
import gem.math.{Angle, HourAngle}
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqActionF
import seqexec.server.keywords.GDSClient
import scala.concurrent.duration.Duration
import org.log4s._

final case class GHOSTController[F[_]: Sync](gdsClient: GDSClient) {
  import GHOSTController._
  val log: Logger = getLogger

  def applyConfig(config: GHOSTConfig): SeqActionF[F, Unit] =
    SeqActionF.apply(log.info(s"Configuring $config"))

  def observe(fileId: ImageFileId): SeqActionF[F, ImageFileId] =
    SeqActionF.apply(fileId)

  def endObserve: SeqActionF[F, Unit] = SeqActionF.void
}

object GHOSTController {

  // TODO: We are going to ignore degrees right now due to time restraints.
  // TODO: I also suspect we are going to want to group ifu info together?
  // TODO: As we don't have GHOST in the OCS2 exported code, we must rely on base types.
  // TODO: Later, we will probably want to use Either, GhostTarget and / or GhostAsterism?
  final case class GHOSTConfig(baseRAHMS: Option[HourAngle],
                               baseDecDMS: Option[Angle],
                               expTime: Duration,
                               srifu1Name: Option[String],
                               srifu1CoordsRAHMS: Option[HourAngle],
                               srifu1CoordsDecDMS: Option[Angle],
                               srifu2Name: Option[String],
                               srifu2CoordsRAHMS: Option[HourAngle],
                               srifu2CoordsDecDMS: Option[Angle],
                               hrifu1Name: Option[String],
                               hrifu1CoordsRAHMS: Option[HourAngle],
                               hrifu1CoordsDecDMS: Option[Angle],
                               hrifu2CoordsRAHMS: Option[HourAngle],
                               hrifu2CoordsDecDMS: Option[Angle]
                              )

  object GHOSTConfig {
    private implicit val durationEq: Eq[Duration] = Eq.by(_.toMillis)
    implicit val eq: Eq[GHOSTConfig] = Eq.by(
      x =>
        (x.baseRAHMS,
         x.baseDecDMS,
         x.expTime,
         x.srifu1Name,
         x.srifu1CoordsRAHMS,
         x.srifu1CoordsDecDMS,
         x.srifu2Name,
         x.srifu2CoordsRAHMS,
         x.srifu2CoordsDecDMS,
         x.hrifu1Name,
         x.hrifu1CoordsRAHMS,
         x.hrifu1CoordsDecDMS,
         x.hrifu2CoordsRAHMS,
         x.hrifu2CoordsDecDMS))

    implicit val show: Show[GHOSTConfig] = Show.fromToString
  }
}
