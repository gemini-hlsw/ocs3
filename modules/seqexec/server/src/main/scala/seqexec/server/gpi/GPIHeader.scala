// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gpi

import gem.Observation
import gem.enum.KeywordName
import seqexec.model.dhs.ImageFileId
import seqexec.server.SeqAction
import seqexec.server.keywords._
import seqexec.server.tcs.TcsKeywordsReader
import seqexec.server.tcs.CRFollow

object GPIHeader {

  def header[A: HeaderProvider](inst: A,
                                gdsClient: GDSClient,
                                tcsKeywordsReader: TcsKeywordsReader,
                                obsKeywordsReader: ObsKeywordsReader): Header =
    new Header {
      override def sendBefore(obsId: Observation.Id,
                              id: ImageFileId): SeqAction[Unit] = {
        val ks = bundleKeywords(
          inst,
          List(
            buildDouble(tcsKeywordsReader.getParallacticAngle
                          .map(_.map(_.toDoubleDegrees))
                          .orDefault,
                        KeywordName.PAR_ANG),
            buildInt32(tcsKeywordsReader.getGpiInstPort.orDefault,
                       KeywordName.INPORT),
            buildBoolean(obsKeywordsReader.getAstrometicField,
                         KeywordName.ASTROMTC),
            buildString(tcsKeywordsReader.getCRFollow.map(
                          _.map(CRFollow.keywordValue).getOrElse("INDEF")),
                        KeywordName.CRFOLLOW)
          )
        )
        ks.flatMap(gdsClient.openObservation(obsId, id, _))
      }

      override def sendAfter(id: ImageFileId): SeqAction[Unit] =
        gdsClient.closeObservation(id)
    }
}
