// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import edu.gemini.seqexec.odb.SeqFailure
import org.http4s.Uri
import seqexec.model.Observation
import seqexec.model.dhs._

sealed trait SeqexecFailure extends Exception with Product with Serializable

object SeqexecFailure {

  /** Seqexec does not know how to deal with instrument in sequence. */
  final case class UnrecognizedInstrument(name: String) extends SeqexecFailure

  /** Something went wrong while running a sequence. * */
  final case class Execution(errMsg: String) extends SeqexecFailure

  /** Aborted sequence * */
  // TODO Reconsider if abort should be handled as an error
  final case class Aborted(obsId: Observation.Id) extends SeqexecFailure

  /** Exception thrown while running a sequence. */
  final case class SeqexecException(ex: Throwable) extends SeqexecFailure

  /** Exception thrown while running a sequence. */
  final case class SeqexecExceptionWhile(context: String, ex: Throwable) extends SeqexecFailure

  /** Invalid operation on a Sequence */
  final case class InvalidOp(errMsg: String) extends SeqexecFailure

  /** Indicates an unexpected problem while performing a Seqexec operation. */
  final case class Unexpected(msg: String) extends SeqexecFailure

  /** Indicates an attempt to enqueue an empty sequence */
  final case class EmptySequence(title: String) extends SeqexecFailure

  /** Timeout */
  final case class Timeout(msg: String) extends SeqexecFailure

  /** Sequence loading errors */
  final case class OdbSeqError(fail: SeqFailure) extends SeqexecFailure

  /** Exception thrown while communicating with the GDS */
  final case class GdsException(ex: Throwable, url: Uri) extends SeqexecFailure

  /** Null epics read */
  final case class NullEpicsError(channel: String) extends SeqexecFailure

  /** Observation command timeout on instrument */
  final case class ObsTimeout(fileId: ImageFileId) extends SeqexecFailure

  /** Observation system timeout, e.g. TCS/GCAL */
  final case class ObsSystemTimeout(fileId: ImageFileId) extends SeqexecFailure

  /** Observation command timeout */
  final case class ObsCommandTimeout(obsId: Observation.Id) extends SeqexecFailure

  /** Failed simulation */
  case object FailedSimulation extends SeqexecFailure

  def explain(f: SeqexecFailure): String = f match {
    case UnrecognizedInstrument(name) => s"Unrecognized instrument: $name"
    case Execution(errMsg)            => s"Sequence execution failed with error: $errMsg"
    case Aborted(obsId)               => s"Observation ${obsId.format} aborted"
    case SeqexecException(ex)         =>
      s"Application exception: ${Option(ex.getMessage).getOrElse(ex.toString)}"
    case SeqexecExceptionWhile(c, e)  =>
      s"Application exception while $c: ${Option(e.getMessage).getOrElse(e.toString)}"
    case InvalidOp(msg)               => s"Invalid operation: $msg"
    case Unexpected(msg)              => s"Unexpected error: $msg"
    case Timeout(msg)                 => s"Timeout while waiting for $msg"
    case EmptySequence(title)         => s"Attempt to enqueue empty sequence $title"
    case OdbSeqError(fail)            => SeqFailure.explain(fail)
    case GdsException(ex, url)        =>
      s"Failure communicating with GDS at $url: ${ex.getMessage}"
    case FailedSimulation             => s"Failed to simulate"
    case NullEpicsError(channel)      => s"Failed to read epics channel: $channel"
    case ObsTimeout(fileId)           => s"Observation of $fileId timed out"
    case ObsSystemTimeout(fileId)     => s"Observation of $fileId timed out on a subsystem"
    case ObsCommandTimeout(obsId)     => s"Observation command on ${obsId.format} timed out"
  }

}
