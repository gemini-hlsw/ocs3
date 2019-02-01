// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.nifs

import cats.Applicative
import gem.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqActionF
import seqexec.server.keywords._

object NifsHeader {

  def header[F[_]: Applicative]: Header[F] =
    new Header[F] {
      override def sendBefore(obsId: Observation.Id,
                              id: ImageFileId): SeqActionF[F, Unit] =
        SeqActionF.void[F]

      override def sendAfter(id: ImageFileId): SeqActionF[F, Unit] =
        SeqActionF.void[F]
    }
}
