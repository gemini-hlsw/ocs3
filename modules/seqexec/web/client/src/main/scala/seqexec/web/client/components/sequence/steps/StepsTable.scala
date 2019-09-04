// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.sequence.steps

import cats.Eq
import cats.data.NonEmptyList
import cats.implicits._
import gem.Observation
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import japgolly.scalajs.react.component.builder.Lifecycle.ComponentWillReceiveProps
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.MonocleReact._
import japgolly.scalajs.react.raw.JsNumber
import monocle.Lens
import monocle.macros.Lenses
import scala.scalajs.js
import scala.math._
import react.common._
import react.common.implicits._
import seqexec.model.enum.Instrument
import seqexec.model.enum.StepType
import seqexec.model.StepState
import seqexec.model.Step
import seqexec.model.StepId
import seqexec.model.StandardStep
import seqexec.model.NodAndShuffleStep
import seqexec.model.NodAndShuffleStatus
import seqexec.model.SequenceState
import seqexec.model.RunningStep
import seqexec.web.client.model.lenses._
import seqexec.web.client.model.ClientStatus
import seqexec.web.client.model.TabOperations
import seqexec.web.client.model.RunOperation
import seqexec.web.client.model.Pages.SeqexecPages
import seqexec.web.client.model.ModelOps._
import seqexec.web.client.model.StepItems._
import seqexec.web.client.model.Formatting._
import seqexec.web.client.circuit.SeqexecCircuit
import seqexec.web.client.circuit.StepsTableAndStatusFocus
import seqexec.web.client.circuit.StepsTableFocus
import seqexec.web.client.actions.ClearAllResourceOperations
import seqexec.web.client.actions.UpdateSelectedStep
import seqexec.web.client.actions.UpdateStepTableState
import seqexec.web.client.actions.FlipBreakpointStep
import seqexec.web.client.components.SeqexecStyles
import seqexec.web.client.components.TableContainer
import seqexec.web.client.semanticui.elements.icon.Icon._
import seqexec.web.client.semanticui.{ Size => SSize }
import seqexec.web.client.reusability._
import react.virtualized._
import web.client.table._

trait Columns {
  val ControlWidth: Double          = 40
  val StepWidth: Double             = 60
  val ExecutionWidth: Double        = 350
  val ExecutionMinWidth: Double     = 350
  val OffsetWidthBase: Double       = 75
  val OffsetIconWidth: Double       = 23.02
  val OffsetPadding: Double         = 12
  val ExposureWidth: Double         = 75
  val ExposureMinWidth: Double      = 83.667 + SeqexecStyles.TableBorderWidth
  val DisperserWidth: Double        = 100
  val DisperserMinWidth: Double     = 100 + SeqexecStyles.TableBorderWidth
  val ObservingModeWidth: Double    = 180
  val ObservingModeMinWidth: Double = 130.8 + SeqexecStyles.TableBorderWidth
  val FilterWidth: Double           = 180
  val FilterMinWidth: Double        = 100
  val FPUWidth: Double              = 100
  val FPUMinWidth: Double           = 46.667 + SeqexecStyles.TableBorderWidth
  val CameraWidth: Double           = 180
  val CameraMinWidth: Double        = 10
  val DeckerWidth: Double           = 110
  val DeckerMinWidth: Double        = 10
  val ImagingMirrorWidth: Double    = 180
  val ImagingMirrorMinWidth: Double = 10
  val ObjectTypeWidth: Double       = 75
  val SettingsWidth: Double         = 34
  val ReadModeMinWidth: Double      = 180
  val ReadModeWidth: Double         = 230

  sealed trait TableColumn extends Product with Serializable
  case object ControlColumn extends TableColumn
  case object StepColumn extends TableColumn
  case object ExecutionColumn extends TableColumn
  case object OffsetColumn extends TableColumn
  case object ObservingModeColumn extends TableColumn
  case object ExposureColumn extends TableColumn
  case object DisperserColumn extends TableColumn
  case object FilterColumn extends TableColumn
  case object FPUColumn extends TableColumn
  case object CameraColumn extends TableColumn
  case object DeckerColumn extends TableColumn
  case object ReadModeColumn extends TableColumn
  case object ImagingMirrorColumn extends TableColumn
  case object ObjectTypeColumn extends TableColumn
  case object SettingsColumn extends TableColumn

  val columnsDefaultWidth: Map[TableColumn, Double] = Map(
    ControlColumn -> ControlWidth,
    StepColumn -> StepWidth,
    ExecutionColumn -> ExposureMinWidth,
    OffsetColumn -> OffsetWidthBase,
    ObservingModeColumn -> ObservingModeWidth,
    ExposureColumn -> ExposureWidth,
    DisperserColumn -> DisperserWidth,
    FilterColumn -> FilterWidth,
    FPUColumn -> FPUWidth,
    CameraColumn -> CameraWidth,
    DeckerColumn -> DeckerWidth,
    ReadModeColumn -> ReadModeWidth,
    ImagingMirrorColumn -> ImagingMirrorWidth,
    ObjectTypeColumn -> ObjectTypeWidth,
    SettingsColumn -> SettingsWidth
  )

  object TableColumn {
    implicit val equal: Eq[TableColumn] = Eq.fromUniversalEquals

    implicit val reuse: Reusability[TableColumn] = Reusability.byRef
  }

  val ControlColumnMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ControlColumn,
    name    = "control",
    label   = "",
    visible = true,
    width   = FixedColumnWidth.unsafeFromDouble(ControlWidth)
  )

  val StepMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    StepColumn,
    name    = "idx",
    label   = "Step",
    visible = true,
    width   = FixedColumnWidth.unsafeFromDouble(StepWidth)
  )

  val ExecutionMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ExecutionColumn,
    name    = "state",
    label   = "Execution Progress",
    visible = true,
    width   = VariableColumnWidth.unsafeFromDouble(0.1, ExecutionMinWidth),
    grow    = 20
  )

  val OffsetMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    OffsetColumn,
    name    = "offsets",
    label   = "Offsets",
    visible = true,
    width   = FixedColumnWidth.unsafeFromDouble(OffsetWidthBase)
  )

  val ObservingModeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObservingModeColumn,
    name    = "obsMode",
    label   = "Observing Mode",
    visible = true,
    width   = VariableColumnWidth.unsafeFromDouble(0.1, ObservingModeMinWidth)
  )

  val ExposureMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ExposureColumn,
    name    = "exposure",
    label   = "Exposure",
    visible = true,
    width   = VariableColumnWidth.unsafeFromDouble(0.1, ExposureMinWidth)
  )

  val DisperserMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    DisperserColumn,
    name    = "disperser",
    label   = "Disperser",
    visible = true,
    width   = VariableColumnWidth.unsafeFromDouble(0.1, DisperserMinWidth)
  )

  val FilterMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    FilterColumn,
    name       = "filter",
    label      = "Filter",
    visible    = true,
    removeable = 2,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, FilterMinWidth)
  )

  val FPUMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    FPUColumn,
    name       = "fpu",
    label      = "FPU",
    removeable = 3,
    visible    = true,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, FPUMinWidth)
  )

  val CameraMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    CameraColumn,
    name       = "camera",
    label      = "Camera",
    visible    = true,
    removeable = 4,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, CameraMinWidth)
  )

  val DeckerMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    DeckerColumn,
    name       = "camera",
    label      = "Decker",
    removeable = 5,
    visible    = true,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, DeckerMinWidth)
  )

  val ReadModeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ReadModeColumn,
    name       = "camera",
    label      = "ReadMode",
    visible    = true,
    removeable = 6,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, ReadModeMinWidth)
  )

  val ImagingMirrorMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ImagingMirrorColumn,
    name       = "camera",
    label      = "ImagingMirror",
    visible    = true,
    removeable = 7,
    width      = VariableColumnWidth.unsafeFromDouble(0.1, ImagingMirrorMinWidth)
  )

  val ObjectTypeMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    ObjectTypeColumn,
    name       = "type",
    label      = "Type",
    visible    = true,
    removeable = 1,
    width      = FixedColumnWidth.unsafeFromDouble(ObjectTypeWidth)
  )

  val SettingsMeta: ColumnMeta[TableColumn] = ColumnMeta[TableColumn](
    SettingsColumn,
    name    = "set",
    label   = "",
    visible = true,
    width   = FixedColumnWidth.unsafeFromDouble(SettingsWidth)
  )

  val all: NonEmptyList[ColumnMeta[TableColumn]] =
    NonEmptyList.of(
      ControlColumnMeta,
      StepMeta,
      ExecutionMeta,
      OffsetMeta,
      ObservingModeMeta,
      ExposureMeta,
      DisperserMeta,
      FilterMeta,
      FPUMeta,
      CameraMeta,
      DeckerMeta,
      ReadModeMeta,
      ImagingMirrorMeta,
      ObjectTypeMeta,
      SettingsMeta
    )

  val allTC = all.map(_.column)

  val columnsMinWidth: Map[TableColumn, Double] = Map(
    ExposureColumn -> ExposureMinWidth,
    DisperserColumn -> DisperserMinWidth,
    ObservingModeColumn -> ObservingModeMinWidth,
    FilterColumn -> FilterMinWidth,
    FPUColumn -> FPUMinWidth,
    CameraColumn -> CameraMinWidth,
    DeckerColumn -> DeckerMinWidth,
    ImagingMirrorColumn -> ImagingMirrorMinWidth,
    ReadModeColumn -> ReadModeMinWidth
  )
}

/**
  * Container for a table with the steps
  */
object StepsTable extends Columns {
  type Backend      = RenderScope[Props, State, Unit]
  type ReceiveProps = ComponentWillReceiveProps[Props, State, Unit]

  private val MIDDLE_BUTTON = 1 // As defined by React.js

  val HeightWithOffsets: Int    = 40
  val BreakpointLineHeight: Int = 5

  // ScalaJS defined trait
  trait StepRow extends js.Object {
    var step: Step
  }

  object StepRow {

    def apply(step: Step): StepRow = {
      val p = (new js.Object).asInstanceOf[StepRow]
      p.step = step
      p
    }

    def unapply(l: StepRow): Option[(Step)] =
      Some((l.step))

    val Zero: StepRow =
      (new js.Object).asInstanceOf[StepRow]
  }

  final case class Props(
    router:     RouterCtl[SeqexecPages],
    canOperate: Boolean,
    stepsTable: StepsTableAndStatusFocus
  ) {
    val status: ClientStatus             = stepsTable.status
    val steps: Option[StepsTableFocus]   = stepsTable.stepsTable
    val instrument: Option[Instrument]   = steps.map(_.instrument)
    val runningStep: Option[RunningStep] = steps.flatMap(_.runningStep)
    val obsId: Option[Observation.Id]    = steps.map(_.id)
    val tableState: TableState[TableColumn] =
      steps.map(_.tableState).getOrElse(State.InitialTableState)
    val stepsList: List[Step]        = steps.foldMap(_.steps)
    val selectedStep: Option[StepId] = steps.flatMap(_.selectedStep)
    val rowCount: Int                = stepsList.length
    val nextStepToRun: Int           = steps.foldMap(_.nextStepToRun).orEmpty
    def tabOperations: TabOperations =
      steps.map(_.tabOperations).getOrElse(TabOperations.Default)
    val showDisperser: Boolean = showProp(InstrumentProperties.Disperser)
    val showExposure: Boolean  = showProp(InstrumentProperties.Exposure)
    val showFilter: Boolean    = showProp(InstrumentProperties.Filter)
    val showFPU: Boolean       = showProp(InstrumentProperties.FPU)
    val showCamera: Boolean    = showProp(InstrumentProperties.Camera)
    val showDecker: Boolean    = showProp(InstrumentProperties.Decker)
    val showImagingMirror: Boolean = showProp(
      InstrumentProperties.ImagingMirror
    )
    val isPreview: Boolean        = steps.map(_.isPreview).getOrElse(false)
    val hasControls: Boolean      = canOperate && !isPreview
    val canSetBreakpoint: Boolean = canOperate && !isPreview
    val showObservingMode: Boolean = showProp(
      InstrumentProperties.ObservingMode
    )
    val showReadMode: Boolean = showProp(InstrumentProperties.ReadMode)

    val sequenceState: Option[SequenceState] = steps.map(_.state)

    def stepSnapshot(step: Step): Option[StepStateSnapshot] =
      (instrument, sequenceState).mapN(StepStateSnapshot(step, _, tabOperations, _))

    def showSecondRow(step: Step): Boolean = stepSnapshot(step).forall(_.displayDetails)

    def secondRowHeight(step: Step): Int = stepSnapshot(step) match {
      case Some(s) if s.displayDetails => SeqexecStyles.runningBottomRowHeight
      case _                           => 0
    }

    def stepSelectionAllowed(sid: StepId): Boolean =
      canControlSubsystems(sid) && !tabOperations.resourceInFlight(sid) && !sequenceState
        .exists(_.isRunning)

    def rowGetter(idx: Int): StepRow =
      steps.flatMap(_.steps.lift(idx)).fold(StepRow.Zero)(StepRow.apply)

    def canControlSubsystems(idx: StepId): Boolean =
      !rowGetter(idx).step.isFinished && canOperate && !isPreview

    val configTableState: TableState[StepConfigTable.TableColumn] =
      stepsTable.configTableState
    // Find out if offsets should be displayed
    val offsetsDisplay: OffsetsDisplay = stepsList.offsetsDisplay
    private def showProp(p: InstrumentProperties): Boolean =
      steps.exists(s => s.instrument.displayItems.contains(p))

    val showOffsets: Boolean =
      stepsList.headOption.flatMap(stepTypeO.getOption) match {
        case Some(StepType.Object) => showProp(InstrumentProperties.Offsets)
        case _                     => false
      }

    val offsetWidth: Option[Double] = {
      val (p, q)     = stepsList.sequenceOffsetWidths
      val labelWidth = max(pLabelWidth, qLabelWidth)
      (max(p, q) + labelWidth + OffsetIconWidth + OffsetPadding * 4).some
    }

    val exposure: Step => Option[String] = s =>
      instrument.flatMap(s.exposureAndCoaddsS)

    val disperser: Step => Option[String] = s => instrument.flatMap(s.disperser)

    val filter: Step => Option[String] = s => instrument.flatMap(s.filter)

    val fpuOrMask: Step => Option[String] = s => instrument.flatMap(s.fpuOrMask)

    val camera: Step => Option[String] = s => instrument.flatMap(s.cameraName)

    val shownForInstrument: List[ColumnMeta[TableColumn]] =
      all.filter {
        case DisperserMeta     => showDisperser
        case OffsetMeta        => showOffsets
        case ObservingModeMeta => showObservingMode
        case ExposureMeta      => showExposure
        case FilterMeta        => showFilter
        case FPUMeta           => showFPU
        case CameraMeta        => showCamera
        case DeckerMeta        => showDecker
        case ImagingMirrorMeta => showImagingMirror
        case ReadModeMeta      => showReadMode
        case _                 => true
      }

    val visibleColumns: TableColumn => Boolean =
      shownForInstrument.map(_.column).contains _

    val extractors = List[(TableColumn, Step => Option[String])](
      (ExposureColumn, exposure),
      (FPUColumn, fpuOrMask),
      (FilterColumn, filter),
      (DisperserColumn, disperser),
      (CameraColumn, camera),
      (DeckerColumn, _.deckerName),
      (ImagingMirrorColumn, _.imagingMirrorName),
      (ObservingModeColumn, _.observingMode),
      (ReadModeColumn, _.readMode)
    ).toMap

    private val valueCalculatedCols: TableColumn => Option[Double] = {
      case ExecutionColumn => 200.0.some
      case OffsetColumn    => offsetWidth
      case _               => none
    }

    private val measuredColumnWidths: TableColumn => Option[Double] =
      colWidthsO(stepsList,
                 allTC,
                 extractors,
                 columnsMinWidth,
                 Map.empty[TableColumn, Double])

    val columnWidths: TableColumn => Option[Double] =
      c => measuredColumnWidths(c).orElse(valueCalculatedCols(c))

  }

  @Lenses
  final case class State(
    tableState:      TableState[TableColumn],
    breakpointHover: Option[Int],
    selected:        Option[StepId],
    scrollCount:     Int,
    scrollWhileRun:  Boolean
  ) {

    def visibleCols(p: Props): State =
      State.columns.set(NonEmptyList.fromListUnsafe(p.shownForInstrument))(this)

    def recalculateWidths(p: Props, size: Size): TableState[TableColumn] =
      tableState.recalculateWidths(size, p.visibleColumns, p.columnWidths)

  }

  object State {
    // Lenses
    val columns: Lens[State, NonEmptyList[ColumnMeta[TableColumn]]] =
      tableState ^|-> TableState.columns[TableColumn]

    val scrollPosition: Lens[State, JsNumber] =
      tableState ^|-> TableState.scrollPosition[TableColumn]

    val userModified: Lens[State, UserModified] =
      tableState ^|-> TableState.userModified[TableColumn]

    val InitialTableState: TableState[TableColumn] =
      TableState(NotModified, 0, all)

    val InitialState: State = State(InitialTableState, None, None, 0, false)
  }

  val stdStepReuse: Reusability[StandardStep] =
    Reusability.caseClassExcept('config)
  implicit val nsStatus: Reusability[NodAndShuffleStatus] =
    Reusability.derive[NodAndShuffleStatus]
  val nsStepReuse: Reusability[NodAndShuffleStep] =
    Reusability.caseClassExcept('config)

  implicit val stepReuse: Reusability[Step] =
    Reusability {
      case (a: StandardStep, b: StandardStep)           => stdStepReuse.test(a, b)
      case (a: NodAndShuffleStep, b: NodAndShuffleStep) => nsStepReuse.test(a, b)
      case _                                            => false
    }
  implicit val propsReuse: Reusability[Props] =
    Reusability.by(x => (x.canOperate, x.selectedStep, x.stepsList))
  implicit val tcReuse: Reusability[TableColumn] = Reusability.byRef
  implicit val stateReuse: Reusability[State] =
    Reusability.by(x => (x.tableState, x.breakpointHover, x.selected))

  private def firstRunnableIndex(l: List[Step]): Int =
    l.zipWithIndex.find(!_._1.isFinished).map(_._2).getOrElse(l.length)

  def stepControlRenderer(
    f:                       StepsTableFocus,
    b:                       Backend,
    rowBreakpointHoverOnCB:  Int => Callback,
    rowBreakpointHoverOffCB: Int => Callback,
    recomputeHeightsCB:      Int => Callback
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      StepToolsCell(
        StepToolsCell.Props(
          b.props.status,
          row.step,
          rowHeight(b)(row.step.id),
          b.props.secondRowHeight(row.step),
          f.isPreview,
          f.nextStepToRun,
          f.id,
          firstRunnableIndex(f.steps),
          rowBreakpointHoverOnCB,
          rowBreakpointHoverOffCB,
          recomputeHeightsCB
        )
      )

  val stepIdRenderer: CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => StepIdCell(row.step.id)

  def settingsControlRenderer(
    p: Props,
    f: StepsTableFocus
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      SettingsCell(
        SettingsCell
          .Props(p.router, f.instrument, f.id, row.step.id, p.isPreview)
      )

  def stepProgressRenderer(
    f: StepsTableFocus,
    b: Backend
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      StepProgressCell(
        StepProgressCell.Props(b.props.status,
                               f.instrument,
                               f.id,
                               f.state,
                               row.step,
                               b.state.selected,
                               b.props.isPreview,
                               b.props.tabOperations)
      )

  def stepStatusRenderer(
    offsetsDisplay: OffsetsDisplay
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      OffsetsDisplayCell(OffsetsDisplayCell.Props(offsetsDisplay, row.step))

  def stepItemRenderer(
    f: Step => Option[String]
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => StepItemCell(StepItemCell.Props(f(row.step)))

  private def stepItemRendererS(f: Step => Option[String]) =
    stepItemRenderer(f(_).map(_.sentenceCase))

  def stepExposureRenderer(
    i: Instrument
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      ExposureTimeCell(ExposureTimeCell.Props(row.step, i))

  def stepFPURenderer(
    i: Instrument
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) => {
      val fpu = row.step
        .fpu(i)
        .orElse(row.step.fpuOrMask(i).map(_.sentenceCase))
      StepItemCell(StepItemCell.Props(fpu))
    }

  def stepObjectTypeRenderer(
    i:    Instrument,
    size: SSize
  ): CellRenderer[js.Object, js.Object, StepRow] =
    (_, _, _, row: StepRow, _) =>
      ObjectTypeCell(ObjectTypeCell.Props(i, row.step, size))

  private val stepRowStyle: Step => Css = {
    case s if s.hasError                       => SeqexecStyles.rowError
    case s if s.status === StepState.Running   => SeqexecStyles.rowWarning
    case s if s.status === StepState.Paused    => SeqexecStyles.rowWarning
    case s if s.status === StepState.Completed => SeqexecStyles.rowDone
    case s if s.status === StepState.Skipped   => SeqexecStyles.rowActive
    case s if s.isFinished                     => SeqexecStyles.rowDone
    case _                                     => SeqexecStyles.stepRow
  }

  private val breakpointRowStyle: Step => Css = {
    case s if s.isFinished => SeqexecStyles.stepDoneWithBreakpoint
    case _                 => SeqexecStyles.stepRowWithBreakpoint
  }

  private val breakpointAndControlRowStyle: Step => Css = {
    case s if s.isFinished => SeqexecStyles.stepDoneWithBreakpointAndControl
    case _                 => SeqexecStyles.stepRowWithBreakpointAndControl
  }

  /**
    * Class for the row depends on properties
    */
  def rowClassName(b: Backend)(i: Int): String =
    ((i,
      b.props.rowGetter(i),
      b.props.canSetBreakpoint,
      b.state.breakpointHover) match {
      case (-1, _, _, _) =>
        // Header
        SeqexecStyles.headerRowStyle
      case (_, StepRow(s), true, _) if s.breakpoint =>
        // row with control elements and breakpoint
        breakpointAndControlRowStyle(b.props.rowGetter(i - 1).step) |+| stepRowStyle(s)
      case (_, StepRow(s), false, _) if s.breakpoint =>
        // row with breakpoint
        breakpointRowStyle(b.props.rowGetter(i - 1).step) |+| stepRowStyle(s)
      case (j, StepRow(s), _, Some(k)) if !s.breakpoint && j === k =>
        // row with breakpoint and hover
        SeqexecStyles.stepRowWithBreakpointHover |+| stepRowStyle(s)
      case (_, StepRow(s), _, _) =>
        // Regular row
        SeqexecStyles.stepRow |+| stepRowStyle(s)
    }).htmlClass

  /**
    * Height depending if we use offsets
    */
  def baseHeight(p: Props): Int =
    if (p.showOffsets) {
      HeightWithOffsets
    } else {
      SeqexecStyles.rowHeight
    }

  /**
    * Calculates the row height depending on conditions
    */
  def rowHeight(b: Backend)(i: Int): Int = {
    val row = b.props.rowGetter(i)
    row match {
      case StepRow(_) if b.props.showSecondRow(row.step) =>
        // Selected
        SeqexecStyles.runningRowHeight + b.props.secondRowHeight(row.step)
      case StepRow(s)
          if s.status === StepState.Running && s.breakpoint =>
        // Row running with a breakpoint set
        SeqexecStyles.runningRowHeight + BreakpointLineHeight
      case StepRow(s) if s.status === StepState.Running =>
        // Row running
        SeqexecStyles.runningRowHeight
      case StepRow(s)
          if b.state.selected.exists(_ === s.id) && !s.skip && b.props.canControlSubsystems(s.id) =>
        // Selected
        SeqexecStyles.runningRowHeight
      case StepRow(s) if s.breakpoint =>
        // Row with a breakpoint set
        baseHeight(b.props) + BreakpointLineHeight
      case _ =>
        // default row
        baseHeight(b.props)
    }
  }

  val columnClassName: TableColumn => Option[Css] = {
    case ControlColumn                => SeqexecStyles.controlCellRow.some
    case StepColumn | ExecutionColumn => SeqexecStyles.paddedStepRow.some
    case ObservingModeColumn | ExposureColumn | DisperserColumn | FilterColumn |
        FPUColumn | CameraColumn | ObjectTypeColumn | DeckerColumn |
        ReadModeColumn | ImagingMirrorColumn =>
      SeqexecStyles.centeredCell.some
    case SettingsColumn => SeqexecStyles.settingsCellRow.some
    case _              => none
  }

  val headerClassName: TableColumn => Option[Css] = {
    case ControlColumn =>
      (SeqexecStyles.centeredCell |+| SeqexecStyles.tableHeaderIcons).some
    case SettingsColumn =>
      (SeqexecStyles.centeredCell |+| SeqexecStyles.tableHeaderIcons).some
    case _ => none
  }

  val controlHeaderRenderer: HeaderRenderer[js.Object] = (_, _, _, _, _, _) =>
    <.span(
      ^.title := "Control",
      IconSettings
  )

  val settingsHeaderRenderer: HeaderRenderer[js.Object] = (_, _, _, _, _, _) =>
    <.span(
      ^.title := "Settings",
      IconBrowser
  )

  private val fixedHeaderRenderer: TableColumn => HeaderRenderer[js.Object] = {
    case ControlColumn  => controlHeaderRenderer
    case SettingsColumn => settingsHeaderRenderer
    case _              => defaultHeaderRendererS
  }

  private def columnCellRenderer(
    b: Backend,
    c: TableColumn): CellRenderer[js.Object, js.Object, StepRow] = {
    val optR = c match {
      case ControlColumn       =>
        b.props.steps.map(
          stepControlRenderer(_,
                              b,
                              rowBreakpointHoverOnCB(b),
                              rowBreakpointHoverOffCB(b),
                              recomputeRowHeightsCB))
      case StepColumn          => stepIdRenderer.some
      case ExecutionColumn     => b.props.steps.map(stepProgressRenderer(_, b))
      case OffsetColumn        => stepStatusRenderer(b.props.offsetsDisplay).some
      case ObservingModeColumn => stepItemRenderer(_.observingMode).some
      case ExposureColumn      => b.props.instrument.map(stepExposureRenderer)
      case DisperserColumn     => b.props.instrument.map(i => stepItemRenderer(_.disperser(i)))
      case FilterColumn        => b.props.instrument.map(i => stepItemRenderer(_.filter(i)))
      case FPUColumn           => b.props.instrument.map(i => stepFPURenderer(i))
      case CameraColumn        => b.props.instrument.map(i => stepItemRenderer(_.cameraName(i)))
      case ObjectTypeColumn    => b.props.instrument.map(stepObjectTypeRenderer(_, SSize.Small))
      case SettingsColumn      => b.props.steps.map(p => settingsControlRenderer(b.props, p))
      case ReadModeColumn      => stepItemRendererS(_.readMode).some
      case DeckerColumn        => stepItemRendererS(_.deckerName).some
      case ImagingMirrorColumn => stepItemRendererS(_.imagingMirrorName).some
      case _                   => none
    }
    optR.getOrElse(defaultCellRendererS)
  }

  // Columns for the table
  private def colBuilder(
    b:    Backend,
    size: Size
  ): ColumnRenderArgs[TableColumn] => Table.ColumnArg = tb => {
    def updateState(s: TableState[TableColumn]): Callback =
      (b.modState(State.tableState.set(s)) *> b.props.obsId
        .map(i => SeqexecCircuit.dispatchCB(UpdateStepTableState(i, s)))
        .getOrEmpty).when_(size.width > 0)

    tb match {
      case ColumnRenderArgs(meta, _, width, true) =>
        Column(
          Column.propsNoFlex(
            width   = width,
            dataKey = meta.name,
            label   = meta.label,
            headerRenderer = resizableHeaderRenderer(
              b.state.tableState
                .resizeColumn(meta.column,
                              size,
                              updateState,
                              b.props.visibleColumns,
                              b.props.columnWidths)
            ),
            headerClassName = headerClassName(meta.column).foldMap(_.htmlClass),
            cellRenderer    = columnCellRenderer(b, meta.column),
            className       = columnClassName(meta.column).foldMap(_.htmlClass)
          )
        )
      case ColumnRenderArgs(meta, _, width, false) =>
        Column(
          Column.propsNoFlex(
            width           = width,
            dataKey         = meta.name,
            label           = meta.label,
            headerRenderer  = fixedHeaderRenderer(meta.column),
            headerClassName = headerClassName(meta.column).foldMap(_.htmlClass),
            cellRenderer    = columnCellRenderer(b, meta.column),
            className       = columnClassName(meta.column).foldMap(_.htmlClass)
          )
        )
    }
  }

  // Called when the scroll position changes
  // This is fairly convoluted as the table always calls this at start when
  // the table is rendered with position 0
  // Aditionally if we programatically scroll to a position we get another call
  // Only after that we assume scroll is user initatied
  def updateScrollPosition(b: Backend, pos: JsNumber): Callback = {
    val posMod            = b.setStateL(State.scrollPosition)(pos)
    // This is done to ignore the scrolls made automatically upon startup
    val hasScrolledBefore = b.state.scrollCount > 2
    val modMod            = b.setStateL(State.userModified)(IsModified).when_(hasScrolledBefore)
    val scrollCountMods   = b.modStateL(State.scrollCount)(_ + 1)
    // Separately calculate the state to send upstream
    val newTs = if (hasScrolledBefore) {
      (State.userModified.set(IsModified) >>> State.scrollPosition.set(pos))(b.state)
    } else {
      State.scrollPosition.set(pos)(b.state)
    }
    val posDiff = abs(pos.toDouble - b.state.tableState.scrollPosition.toDouble)
    // If running store it on the state
    // We'll consider that the user has moved intentionally if the scroll is more than 2 rows
    val scrolledWhileRunningMod = b.setStateL(State.scrollWhileRun)(true)
      .when_(b.props.sequenceState.exists(_.isRunning) && posDiff > 2*(HeightWithOffsets + BreakpointLineHeight))
    // Always update the position and counts, but the modification flag only if
    // we are sure the user did scroll manually
    (posMod *>
      scrollCountMods *>
      modMod *>
      scrolledWhileRunningMod *>
      // And silently update the model
      b.props.obsId
        .map(id =>
          SeqexecCircuit.dispatchCB(UpdateStepTableState(id, newTs.tableState))).getOrEmpty)
      .when_(posDiff > 1) // Only update the state if the change is significant
  }

  def startScrollTop(state: State): js.UndefOr[JsNumber] =
    if (state.tableState.isModified) {
      state.tableState.scrollPosition
    } else {
      js.undefined
    }

  def startScrollToIndex(b: Backend): Int =
    if (b.state.tableState.isModified) {
      -1
    } else {
      max(0, b.props.nextStepToRun - 1)
    }

  // Single click puts the row as selected
  def singleClick(b: Backend)(i: Int): Callback =
    b.props.obsId.map { id =>
      (SeqexecCircuit
        .dispatchCB(UpdateSelectedStep(id, i)) *>
        SeqexecCircuit
          .dispatchCB(ClearAllResourceOperations(id)) *>
        b.modState(State.selected.set(Some(i))) *>
        recomputeRowHeightsCB(min(b.state.selected.getOrElse(i), i)))
        .when_(b.props.stepSelectionAllowed(i))
    }.getOrEmpty

  def stepsTableProps(b: Backend)(size: Size): Table.Props =
    Table.props(
      disableHeader = false,
      noRowsRenderer = () =>
        <.div(
          ^.cls := "ui center aligned segment noRows",
          ^.height := size.height.px,
          "No Steps"
        ),
      overscanRowCount = SeqexecStyles.overscanRowCount,
      height           = max(1, size.height.toInt),
      rowCount         = b.props.rowCount,
      rowHeight        = rowHeight(b) _,
      rowClassName     = rowClassName(b) _,
      width            = max(1, size.width.toInt),
      rowGetter        = b.props.rowGetter _,
      scrollToIndex    = startScrollToIndex(b),
      scrollTop        = startScrollTop(b.state),
      onRowClick       = singleClick(b),
      onScroll =
        (a, _, pos) => updateScrollPosition(b, pos).when_(a.toDouble > 0),
      scrollToAlignment = ScrollToAlignment.Center,
      headerClassName   = SeqexecStyles.tableHeader.htmlClass,
      headerHeight      = SeqexecStyles.headerHeight,
      rowRenderer       = stepsRowRenderer(b.props)
    )

  // We want clicks to be processed only if the click is not on the first row with the breakpoint/skip controls
  private def allowedClick(
    p:          Props,
    index:      Int,
    onRowClick: Option[OnRowClick]
  )(e:          ReactMouseEvent): Callback =
    // If alt is pressed or middle button flip the breakpoint
    if (e.altKey || e.button === MIDDLE_BUTTON) {
      (p.obsId, p.stepsList.find(_.id === index + 1))
        .mapN(
          (oid, step) =>
            SeqexecCircuit
              .dispatchCB(FlipBreakpointStep(oid, step))
              .when_(step.canSetBreakpoint(index + 1, p.nextStepToRun))
        )
        .getOrEmpty
        .when_(p.canSetBreakpoint)
    } else {
      onRowClick
        .filter(_ => e.clientX > ControlWidth)
        .map(h => h(index))
        .getOrEmpty
    }

  private def stepsRowRenderer(p: Props) =
    (className:        String,
     columns:          Array[VdomNode],
     index:            Int,
     _:                Boolean,
     key:              String,
     _:                StepRow,
     onRowClick:       Option[OnRowClick],
     onRowDoubleClick: Option[OnRowClick],
     _:                Option[OnRowClick],
     _:                Option[OnRowClick],
     _:                Option[OnRowClick],
     style:            Style) => {
      p.rowGetter(index) match {
        case StepRow(s) if p.showSecondRow(s) && index === s.id =>
          <.div(
            ^.key := key,
            ^.style := Style.toJsObject(style),
            SeqexecStyles.expandedRunningRow,
            <.div(
              ^.cls := className,
              ^.key := s"$key-top",
              SeqexecStyles.expandedTopRow,
              ^.onClick ==> allowedClick(p, index, onRowClick),
              ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
              columns.toTagMod
            ),
            p.stepSnapshot(s).map {s =>
              <.div(
                SeqexecStyles.expandedBottomRow,
                SeqexecStyles.acProgressRow,
              ^.onClick ==> allowedClick(p, index, onRowClick),
              ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
                AlignAndCalibProgress(AlignAndCalibProgress.Props(s)),
                ^.key := s"$key-base"
              )}
          )
        case _ =>
            <.div(
              ^.cls := className,
              ^.key := key,
              ^.role := "row",
              ^.style := Style.toJsObject(style),
              ^.onClick ==> allowedClick(p, index, onRowClick),
              ^.onDoubleClick -->? onRowDoubleClick.map(h => h(index)),
              columns.toTagMod
            )
      }
    }

  // Create a ref
  private val ref = Ref.toJsComponent(Table.component)

  private def recomputeRowHeightsCB(index: Int): Callback =
    ref.get.flatMapCB(_.raw.recomputeRowsHeightsCB(index))

  def rowBreakpointHoverOnCB(b: Backend)(index: Int): Callback =
    (if (b.props.rowGetter(index).step.breakpoint)
       b.modState(State.breakpointHover.set(None))
     else b.modState(State.breakpointHover.set(index.some))) *>
      recomputeRowHeightsCB(index)

  def rowBreakpointHoverOffCB(b: Backend)(index: Int): Callback =
    b.modState(State.breakpointHover.set(None)) *> recomputeRowHeightsCB(index)

  private def updateStep(b:     ReceiveProps,
                         p:     Props,
                         obsId: Observation.Id,
                         i:     StepId): Callback =
    SeqexecCircuit.dispatchCB(UpdateSelectedStep(obsId, i)) *>
      b.setStateL(State.selected)(i.some)
        .when_(p.canControlSubsystems(i))

  private def scrollTo(i: StepId): Callback =
    ref.get.flatMapCB(_.raw.scrollToRowCB(i))

  // Scroll to pos on run requested
  private def scrollToCB(cur: Props, next: Props): Callback =
    scrollTo(next.nextStepToRun)
      .when_(
        cur.tabOperations.runRequested =!= next.tabOperations.runRequested
          && next.tabOperations.runRequested === RunOperation.RunInFlight
      )

  private def selectStepCB(b: ReceiveProps): Callback = {
    val (cur: Props, next: Props) = (b.currentProps, b.nextProps)
    // Update the selected step as the run proceeds
    (next.obsId, cur.runningStep, next.runningStep) match {
      case (Some(obsId), Some(RunningStep(i, _)), None) =>
        // This happens when a sequence stops, e.g. with a pasue
        updateStep(b, next, obsId, i) *>
          // And reset scrolling state
          b.setStateL(State.scrollWhileRun)(false)
      case (Some(obsId), Some(RunningStep(i, _)), Some(RunningStep(j, _)))
          if i =!= j =>
        // This happens when we keep running and move to the next step
        // If the user hasn't scrolled we'll focus on the next step
        updateStep(b, next, obsId, j) *>
          scrollTo(j).unless_(b.state.scrollWhileRun)
      case (Some(obsId), _, Some(RunningStep(j, _)))
          if cur.sequenceState =!= next.sequenceState && next.sequenceState.exists(_.isRunning) =>
        // When we start running
        updateStep(b, next, obsId, j)
      case _ =>
        Callback.empty
    }
  }

  private def selectedStepChangeCB(b: ReceiveProps): Callback = {
    val (cur: Props, next: Props) = (b.currentProps, b.nextProps)
    // The selected step may have changed externally
    (cur.selectedStep, next.selectedStep).mapN { (c, n) =>
      b.setStateL(State.selected)(n.some).when(c =!= n).void
    }.getOrEmpty
  }

  // We need to update the state if the props change
  def receiveNewProps(b: ReceiveProps): Callback = {
    val (cur: Props, next: Props) = (b.currentProps, b.nextProps)
    // Recalculate the heights if needed
    val stepsPairs = next.stepsList.zip(cur.stepsList)
    val differentStepsStates: List[StepId] = stepsPairs.collect {
      // if step status changes recalculate
      case (cur, prev) if cur.status =!= prev.status =>
        cur.id
      // if breakpoint state changes recalculate
      case (cur, prev) if cur.breakpoint =!= prev.breakpoint =>
        cur.id
    }

    // If the selected step changes recompute height
    val selected: Option[StepId] =
      (cur.selectedStep, next.selectedStep)
        .mapN { (c, n) =>
          min(c, n)
        }
        .filter(_ => b.state.selected =!= next.selectedStep)

    // If the step is running recalculate height
    val running: Option[StepId] =
      if (cur.tabOperations.resourceRunRequested =!= next.tabOperations.resourceRunRequested) {
        next.selectedStep
      } else {
        none
      }

    // Decide from what row to recalculate
    val recalculateHeight: Callback =
      (running.toList ::: selected.toList ::: differentStepsStates).minimumOption.map {
        recomputeRowHeightsCB
      }.getOrEmpty

    scrollToCB(cur, next) *>
      selectStepCB(b) *>
      selectedStepChangeCB(b) *>
      recalculateHeight
  }

  // Wire it up from VDOM
  def render(b: Backend): VdomElement =
    TableContainer(
      TableContainer.Props(
        b.props.hasControls,
        size => {
          val ts =
            b.state.tableState
              .columnBuilder(size, colBuilder(b, size), b.props.columnWidths)
              .map(_.vdomElement)

          if (size.width > 0) {
            ref
              .component(stepsTableProps(b)(size))(ts: _*)
              .vdomElement
          } else {
            <.div()
          }
        },
        onResize = s =>
          b.modStateL(State.tableState)(
            _.recalculateWidths(s,
                                b.props.visibleColumns,
                                b.props.columnWidths))
      ))

  def initialState(p: Props): State =
    (State.tableState.set(p.tableState) >>> State.selected.set(p.selectedStep))(State.InitialState)

  private val component = ScalaComponent
    .builder[Props]("StepsTable")
    .initialStateFromProps(initialState)
    .render(render)
    .configure(Reusability.shouldComponentUpdate)
    .componentWillReceiveProps(receiveNewProps)
    .build

  def apply(p: Props): Unmounted[Props, State, Unit] = component(p)
}
