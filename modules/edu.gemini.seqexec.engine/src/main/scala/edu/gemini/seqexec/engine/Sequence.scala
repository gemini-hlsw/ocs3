// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.engine

import edu.gemini.seqexec.model.Model.{Observer, Resource, SequenceMetadata, SequenceState, StepState}
import edu.gemini.seqexec.engine.Step._

import scalaz._
import Scalaz._
import scalaz.concurrent.Task

import monocle.Lens
import monocle.macros.GenLens

/**
  * A list of `Step`s grouped by target and instrument.
  */
final case class Sequence[+A](
  id: Sequence.Id,
  metadata: SequenceMetadata,
  steps: List[Step[A]]
) {

  // The Monoid mappend of a Set is a Set union
  val resources: Set[Resource] = steps.foldMap(_.resources)

}

object Sequence {

  type Id = String

  def empty[A](id: Id, m: SequenceMetadata): Sequence[A] = Sequence(id, m, Nil)

  implicit val sequenceFunctor: Functor[Sequence] = new Functor[Sequence] {
    def map[A, B](fa: Sequence[A])(f: A => B): Sequence[B] =
      Sequence(fa.id, fa.metadata, fa.steps.map(_.map(f)))
  }

  implicit val stepFoldable: Foldable[Sequence] = new Foldable[Sequence] {
    def foldMap[A, B](fa: Sequence[A])(f: A => B)(implicit F: Monoid[B]): B =
      fa.steps.foldMap(_.foldMap(f))

    def foldRight[A, B](fa: Sequence[A], z: => B)(f: (A, => B) => B): B =
      fa.steps.foldRight(z)((l, b) => l.foldRight(b)(f(_, _)))
  }

  def metadata[A]: Lens[Sequence[A], SequenceMetadata] =
    GenLens[Sequence[A]](_.metadata)

  /**
    * Sequence Zipper. This structure is optimized for the actual `Sequence`
    * execution.
    *
    */
  final case class Zipper(
    id: Id,
    metadata: SequenceMetadata,
    pending: List[Step[Action]],
    focus: Step.Zipper,
    done: List[Step[Result]]
  ) {

    /**
      * Runs the next execution. If the current `Step` is completed it adds the
      * `StepZ` under focus to the list of completed `Step`s and makes the next
      * pending `Step` the current one.
      *
      * If there are still `Execution`s that have not finished in the current
      * `Step` or if there are no more pending `Step`s it returns `None`.
      */
    val next: Option[Zipper] =
      focus.next match {
        // Step completed
        case None      =>
          pending match {
            case Nil             => None
            case stepp :: stepps =>
              (Step.Zipper.currentify(stepp) |@| focus.uncurrentify) (
                (curr, stepd) => Zipper(id, metadata, stepps, curr, done :+ stepd)
              )
          }
        // Current step ongoing
        case Some(stz) => Some(Zipper(id, metadata, pending, stz, done))
      }

    def rollback: Zipper = this.copy(focus = focus.rollback)

    /**
      * Obtain the resulting `Sequence` only if all `Step`s have been completed.
      * This is a special way of *unzipping* a `Zipper`.
      *
      */
    val uncurrentify: Option[Sequence[Result]] =
      if (pending.isEmpty) focus.uncurrentify.map(x => Sequence(id, metadata, done :+ x))
      else None

    /**
      * Unzip a `Zipper`. This creates a single `Sequence` with either
      * completed `Step`s or pending `Step`s.
      */
    val toSequence: Sequence[Action \/ Result] =
      Sequence(
        id,
        metadata,
        // TODO: Functor composition?
        done.map(_.map(_.right)) ++
          List(focus.toStep) ++
          pending.map(_.map(_.left))
      )
  }

  object Zipper {

    /**
      * Make a `Zipper` from a `Sequence` only if all the `Step`s in the
      * `Sequence` are pending. This is a special way of *zipping* a `Sequence`.
      *
      */
    def currentify(seq: Sequence[Action]): Option[Zipper] =
      seq.steps match {
        case Nil           => None
        case step :: steps =>
          Step.Zipper.currentify(step).map(
            Zipper(seq.id, seq.metadata, steps, _, Nil)
          )
      }

    def zipper(seq: Sequence[Action \/ Result]): Option[Zipper] =
      separate(seq).flatMap {
        case (pending, done)   => pending match {
          case Nil             => None
          case s :: ss =>
            Step.Zipper.currentify(s).map(
              Zipper(seq.id, seq.metadata, ss, _, done)
            )
        }
      }

    // We would use MonadPlus' `separate` if we wanted to separate Actions or
    // Results, but here we want only Steps.
    private def separate(seq: Sequence[Action \/ Result]): Option[(List[Step[Action]], List[Step[Result]])] = {

      seq.steps.foldLeftM[Option, (List[Step[Action]], List[Step[Result]])]((Nil, Nil))(
        (acc, step) =>
        if (Step.status(step) === StepState.Pending)
          Some(
            acc.leftMap(
              _ :+ step.map(
                _.fold(
                  identity,
                  // It should never happen
                  _ => fromTask(Task(Result.Error("Inconsistent status")))
                )
              )
            )
          )
        else if (Step.status(step) === StepState.Completed)
          Some(
            acc.rightMap(
              _ :+ step.map(
                _.fold(
                  // It should never happen
                  _ => Result.Error("Inconsistent status"),
                  identity
                )
              )
            )
          )
        else None
      )

    }

    val focus: Lens[Zipper, Step.Zipper] =
      GenLens[Zipper](_.focus)

    val current: Lens[Zipper, Execution] =
      focus ^|-> Step.Zipper.current

    val metadata: Lens[Zipper, SequenceMetadata] =
      GenLens[Zipper](_.metadata)

  }

  sealed trait State {

    /**
      * Returns a new `State` where the next pending `Step` is been made the
      * current `Step` under execution and the previous current `Step` is
      * placed in the completed `Sequence`.
      *
      * If the current `Step` has `Execution`s not completed or there are no more
      * pending `Step`s it returns `None`.
      */
    val next: Option[State]

    val status: SequenceState

    val pending: List[Step[Action]]

    def rollback: State

    def setBreakpoint(stepId: Step.Id, v: Boolean): State

    def getCurrentBreakpoint: Boolean

    def setObserver(name: Observer): State

    /**
      * Current Execution
      */
    val current: Execution

    val done: List[Step[Result]]

    /**
      * Given an index of a current `Action` it replaces such `Action` with the
      * `Result` and returns the new modified `State`.
      *
      * If the index doesn't exist, the new `State` is returned unmodified.
      */
    def mark(i: Int)(r: Result): State

    /**
      * Unzip `State`. This creates a single `Sequence` with either completed `Step`s
      * or pending `Step`s.
      */
    val toSequence: Sequence[Action \/ Result]

  }

  object State {

    val status: Lens[State, SequenceState] =
    // `State` doesn't provide `.copy`
      Lens[State, SequenceState](_.status)(s => a => a match {
        case Zipper(st, _) => Zipper(st, s)
        case Final(st, _)  => Final(st, s)
      })

    /**
      * Initialize a `State` passing a `Queue` of pending `Sequence`s.
      */
    // TODO: Make this function `apply`?
    def init(q: Sequence[Action \/ Result]): State =
      Sequence.Zipper.zipper(q).map(Zipper(_, SequenceState.Idle))
        .getOrElse(Final(Sequence.empty(q.id, q.metadata), SequenceState.Idle))

    /**
      * This is the `State` in Zipper mode, which means is under execution.
      *
      */
    final case class Zipper(zipper: Sequence.Zipper, status: SequenceState) extends State { self =>

      override val next: Option[State] = zipper.next match {
        // Last execution
        case None    => zipper.uncurrentify.map(Final(_, status))
        case Some(x) => Zipper(x, status).some
      }

      /**
        * Current Execution
        */
      override val current: Execution =
        // Queue
        zipper
          // Step
          .focus
          // Execution
          .focus

      override val pending: List[Step[Action]] = zipper.pending

      override def rollback: Zipper = self.copy(zipper = zipper.rollback)

      override def setBreakpoint(stepId: Step.Id, v: Boolean): State = self.copy(zipper =
        zipper.copy(pending =
          zipper.pending.map(s => if(s.id == stepId) s.copy(breakpoint = v) else s)))

      override def getCurrentBreakpoint: Boolean = zipper.focus.breakpoint && zipper.focus.done.isEmpty

      override def setObserver(name: Observer): State = observerL.set(name.some)(self)

      override val done: List[Step[Result]] = zipper.done

      private val zipperL: Lens[Zipper, Sequence.Zipper] =
        GenLens[Zipper](_.zipper)

      private val metadataL: Lens[Zipper, SequenceMetadata] =
        zipperL ^|-> Sequence.Zipper.metadata

      private val observerL: Lens[Zipper, Option[Observer]] =
        metadataL ^|-> SequenceMetadata.observer

      override def mark(i: Int)(r: Result): State = {

        val currentExecutionL: Lens[Zipper, Execution] = zipperL ^|-> Sequence.Zipper.current

        val currentFileIdL: Lens[Zipper, Option[FileId]] =
          zipperL ^|-> Sequence.Zipper.focus ^|-> Step.Zipper.fileId

        val z: Zipper = r match {
            case Result.Partial(Result.FileIdAllocated(fileId), _) => currentFileIdL.set(fileId.some)(self)
            case _                                                 => self
          }

        currentExecutionL.modify(_.mark(i)(r))(z)

      }

      override val toSequence: Sequence[Action \/ Result] = zipper.toSequence

    }

    /**
      * Final `State`. This doesn't have any `Step` under execution, there are
      * only completed `Step`s.
      *
      */
    final case class Final(seq: Sequence[Result], status: SequenceState) extends State { self =>

      override val next: Option[State] = None

      override val current: Execution = Execution.empty

      override val pending: List[Step[Action]] = Nil

      override def rollback = self

      override def setBreakpoint(stepId: Step.Id, v: Boolean): State = self

      override def getCurrentBreakpoint: Boolean = false

      override def setObserver(name: Observer): State = observerL.set(name.some)(self)

      override val done: List[Step[Result]] = seq.steps

      override def mark(i: Int)(r: Result): State = self

      override val toSequence: Sequence[Action \/ Result] = seq.map(_.right)

      private val sequenceL: Lens[Final, Sequence[Result]] =
        GenLens[Final](_.seq)

      private val metadataL: Lens[Final, SequenceMetadata] =
        sequenceL ^|-> Sequence.metadata

      private val observerL: Lens[Final, Option[Observer]] =
        metadataL ^|-> SequenceMetadata.observer

    }

  }

}
