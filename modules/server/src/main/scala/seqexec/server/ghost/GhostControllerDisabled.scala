// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.ghost

import cats.Applicative
import cats.implicits._
import org.typelevel.log4cats.Logger
import seqexec.model.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.overrideLogMessage
import seqexec.server.keywords.GdsClient
import seqexec.server.keywords.KeywordBag
import squants.time.Time

class GhostControllerDisabled[F[_]: Logger: Applicative] extends GhostController[F] {
  private val name = "GHOST"

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

  override def applyConfig(config: GhostConfig): F[Unit] = overrideLogMessage(name, "applyConfig")

  override def observe(fileId: ImageFileId, expTime: Time): F[ImageFileId] =
    overrideLogMessage(name, s"observe $fileId").as(fileId)

  override def endObserve: F[Unit] = overrideLogMessage(name, "endObserve")
}
