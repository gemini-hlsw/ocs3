// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import gem.Observation
import edu.gemini.spModel.config2.Config
import seqexec.server.keywords._

/**
  * Describes the parameters for an observation
  */
final case class ObserveEnvironment[F[_]](
  systems:  Systems[F],
  config:   Config,
  stepType: StepType,
  obsId:    Observation.Id,
  inst:     InstrumentSystem[F],
  otherSys: List[System[F]],
  headers:  HeaderExtraData => List[Header[F]],
  ctx:      HeaderExtraData
)
