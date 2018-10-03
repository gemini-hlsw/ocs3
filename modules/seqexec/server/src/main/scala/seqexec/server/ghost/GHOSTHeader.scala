// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.ghost

import gem.Observation
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqAction
import seqexec.server.keywords._

object GHOSTHeader {

  def header(): Header =
    new Header {
      override def sendBefore(obsId: Observation.Id,
                              id: ImageFileId): SeqAction[Unit] =
        SeqAction.void


      override def sendAfter(id: ImageFileId): SeqAction[Unit] =
        SeqAction.void
    }
}
