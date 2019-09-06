// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gpi

import cats._
import cats.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import seqexec.engine.ParallelActions
import seqexec.engine.Result
import seqexec.server.StepType
import seqexec.server.InstrumentActions
import seqexec.server.ObserveActions
import seqexec.server.ObserveEnvironment

/**
  * Gpi needs different actions for A&C
  */
class GpiInstrumentActions[F[_]: MonadError[?[_], Throwable]: Logger]
    extends InstrumentActions[F] {

  override def observationProgressStream(
    env: ObserveEnvironment[F]
  ): Stream[F, Result[F]] =
    ObserveActions.observationProgressStream(env)

  override def observeActions(
    env:  ObserveEnvironment[F],
    post: (Stream[F, Result[F]], ObserveEnvironment[F]) => Stream[F, Result[F]]
  ): List[ParallelActions[F]] =
    if (env.stepType === StepType.AlignAndCalib) {
      Nil
    } else {
      InstrumentActions.defaultInstrumentActions[F].observeActions(env, post)
    }

  override def runInitialAction(stepType: StepType): Boolean =
    stepType =!= StepType.AlignAndCalib

}
