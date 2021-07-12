// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gpi

import cats.Applicative
import cats.implicits._
import giapi.client.GiapiStatusDb
import org.typelevel.log4cats.Logger
import seqexec.model.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.overrideLogMessage
import seqexec.server.keywords.GdsClient
import seqexec.server.keywords.KeywordBag
import squants.time.Time

class GpiControllerDisabled[F[_]: Logger: Applicative](override val statusDb: GiapiStatusDb[F])
    extends GpiController[F] {
  private val name = "GPI"

  override def gdsClient: GdsClient[F] = new GdsClient[F] {
    override def setKeywords(id: ImageFileId, ks: KeywordBag): F[Unit] =
      overrideLogMessage(name, "setKeywords")

    override def openObservation(obsId: Observation.Id, id: ImageFileId, ks: KeywordBag): F[Unit] =
      overrideLogMessage(name, "openObservation")

    override def closeObservation(id: ImageFileId): F[Unit] =
      overrideLogMessage(name, "closeObservation")

    override def abortObservation(id: ImageFileId): F[Unit] =
      overrideLogMessage(name, "abortObservation")
  }

  override def alignAndCalib: F[Unit] = overrideLogMessage(name, "alignAndCalib")

  override def applyConfig(config: GpiConfig): F[Unit] = overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, expTime: Time): F[ImageFileId] =
    overrideLogMessage(name, s"observe $fileId").as(fileId)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")
}
