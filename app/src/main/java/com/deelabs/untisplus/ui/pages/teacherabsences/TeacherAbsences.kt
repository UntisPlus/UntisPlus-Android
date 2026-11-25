package com.deelabs.untisplus.ui.pages.teacherabsences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.AnalyticsBucket
import com.deelabs.untisplus.api.model.proxy.AnalyticsChangeCount
import com.deelabs.untisplus.api.model.proxy.AnalyticsElementReference
import com.deelabs.untisplus.api.model.proxy.AnalyticsLessonChange
import com.deelabs.untisplus.api.model.proxy.AnalyticsOptions
import com.deelabs.untisplus.api.model.proxy.AnalyticsReport
import com.deelabs.untisplus.api.model.proxy.AnalyticsTarget
import com.deelabs.untisplus.ui.common.AppScaffold
import com.deelabs.untisplus.ui.dialogs.DatePickerDialog
import com.deelabs.untisplus.ui.functional.bottomInsets
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherAbsences(
	viewModel: TeacherAbsencesViewModel = koinViewModel()
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	var pickingFrom by remember { mutableStateOf(false) }
	var pickingTo by remember { mutableStateOf(false) }

	AppScaffold(
		modifier = Modifier.bottomInsets(),
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(stringResource(id = R.string.activity_title_teacher_absences)) },
				navigationIcon = {
					IconButton(onClick = { viewModel.goBack() }) {
						Icon(
							imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
							contentDescription = stringResource(id = R.string.all_back)
						)
					}
				}
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			when {
				state.supported == null -> {
					CircularProgressIndicator()
				}

				state.supported == false -> {
					Text(stringResource(R.string.teacher_absences_unsupported))
				}

				state.reports.isNotEmpty() -> {
					TextButton(onClick = { viewModel.backToSetup() }) {
						Text(stringResource(R.string.teacher_absences_back))
					}
					state.reports.forEach { entry ->
						Text(
							text = stringResource(R.string.teacher_absences_report_for, entry.label),
							style = MaterialTheme.typography.titleMedium
						)
						ReportDashboard(entry = entry)
					}
				}

				else -> {
					ReportSetup(
						options = state.options,
						loadingOptions = state.loadingOptions,
						optionsError = state.optionsError,
						selectedMode = state.selectedMode,
						searchQuery = state.searchQuery,
						selectedTarget = state.selectedTarget,
						selectedDataSource = state.selectedDataSource,
						schoolYears = state.schoolYears,
						selectedYears = state.selectedYears,
						customRange = state.customRange,
						from = state.from,
						to = state.to,
						invalidRange = state.invalidRange,
						tooManyYears = state.tooManyYears,
						loadingReport = state.loadingReport,
						reportError = state.reportError,
						failedYears = state.failedYears,
						onModeSelected = { viewModel.selectMode(it) },
						onSearchChanged = { viewModel.setSearchQuery(it) },
						onTargetSelected = { viewModel.selectTarget(it) },
						onChangeTarget = { viewModel.clearTarget() },
						onDataSourceSelected = { viewModel.selectDataSource(it) },
						onYearToggled = { viewModel.toggleYear(it) },
						onUseSchoolYears = { viewModel.useSchoolYears() },
						onUseCustomRange = { viewModel.useCustomRange() },
						onPickFrom = { pickingFrom = true },
						onPickTo = { pickingTo = true },
						onRun = { viewModel.runReport() },
						onRetryOptions = { viewModel.retryOptions() }
					)
				}
			}
		}
	}

	if (pickingFrom) {
		DatePickerDialog(
			initialSelection = state.from,
			onDismiss = { pickingFrom = false },
			onDateSelected = {
				viewModel.setFrom(it)
				pickingFrom = false
			}
		)
	}
	if (pickingTo) {
		DatePickerDialog(
			initialSelection = state.to,
			onDismiss = { pickingTo = false },
			onDateSelected = {
				viewModel.setTo(it)
				pickingTo = false
			}
		)
	}
}

@Composable
private fun ReportSetup(
	options: AnalyticsOptions?,
	loadingOptions: Boolean,
	optionsError: Boolean,
	selectedMode: AnalyticsReportMode?,
	searchQuery: String,
	selectedTarget: AnalyticsTarget?,
	selectedDataSource: String?,
	schoolYears: List<TeacherAbsencesYear>,
	selectedYears: Set<String>,
	customRange: Boolean,
	from: LocalDate,
	to: LocalDate,
	invalidRange: Boolean,
	tooManyYears: Boolean,
	loadingReport: Boolean,
	reportError: String?,
	failedYears: List<String>,
	onModeSelected: (AnalyticsReportMode) -> Unit,
	onSearchChanged: (String) -> Unit,
	onTargetSelected: (AnalyticsTarget) -> Unit,
	onChangeTarget: () -> Unit,
	onDataSourceSelected: (String?) -> Unit,
	onYearToggled: (String) -> Unit,
	onUseSchoolYears: () -> Unit,
	onUseCustomRange: () -> Unit,
	onPickFrom: () -> Unit,
	onPickTo: () -> Unit,
	onRun: () -> Unit,
	onRetryOptions: () -> Unit,
) {
	when {
		loadingOptions -> {
			CircularProgressIndicator()
			Text(stringResource(R.string.teacher_absences_loading_options))
		}

		optionsError -> {
			Text(stringResource(R.string.teacher_absences_error_options))
			Button(onClick = onRetryOptions) { Text(stringResource(R.string.all_reset)) }
		}

		options == null -> {
			Text(stringResource(R.string.teacher_absences_error_options))
		}

		else -> {
			Text(
				text = stringResource(R.string.teacher_absences_select_type),
				style = MaterialTheme.typography.titleMedium
			)
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				AnalyticsReportMode.entries.forEach { mode ->
					FilterChip(
						selected = selectedMode == mode,
						onClick = { onModeSelected(mode) },
						label = { Text(stringResource(mode.labelRes)) },
						modifier = Modifier.fillMaxWidth()
					)
				}
			}

			if (selectedMode == null) return

			val targets = options.filteredTargets(selectedMode, searchQuery)
			Text(
				text = stringResource(R.string.teacher_absences_select_target),
				style = MaterialTheme.typography.titleMedium
			)
			if (selectedTarget != null) {
				ListItem(
					headlineContent = { Text(selectedTarget.name) },
					supportingContent = { Text(stringResource(R.string.teacher_absences_selected)) },
					modifier = Modifier.fillMaxWidth()
				)
				TextButton(onClick = onChangeTarget) {
					Text(stringResource(R.string.teacher_absences_change_target))
				}
			} else {
				OutlinedTextField(
					value = searchQuery,
					onValueChange = onSearchChanged,
					label = { Text(stringResource(R.string.teacher_absences_search)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				if (targets.isEmpty()) {
					Text(stringResource(R.string.teacher_absences_no_targets))
				} else {
					targets.forEach { target ->
						ListItem(
							headlineContent = { Text(target.name) },
							supportingContent = { Text(target.mode) },
							modifier = Modifier
								.fillMaxWidth()
								.clickable { onTargetSelected(target) }
						)
					}
				}
			}

			if (selectedTarget != null) {
				Text(
					text = stringResource(R.string.teacher_absences_select_source),
					style = MaterialTheme.typography.titleMedium
				)
				Text(stringResource(R.string.teacher_absences_source_hint))
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					FilterChip(
						selected = selectedDataSource == null,
						onClick = { onDataSourceSelected(null) },
						label = { Text(stringResource(R.string.teacher_absences_source_automatic)) },
						modifier = Modifier.fillMaxWidth()
					)
					selectedTarget.dataSources.forEach { source ->
						FilterChip(
							selected = selectedDataSource == source,
							onClick = { onDataSourceSelected(source) },
							label = { Text(dataSourceLabel(source)) },
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			}

			Text(
				text = stringResource(R.string.teacher_absences_select_period),
				style = MaterialTheme.typography.titleMedium
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.teacher_absences_school_years)) },
				supportingContent = { Text(stringResource(R.string.teacher_absences_school_year_hint)) },
				modifier = Modifier
					.fillMaxWidth()
					.clickable(onClick = onUseSchoolYears)
			)
			if (schoolYears.isEmpty()) {
				Text(stringResource(R.string.teacher_absences_no_years))
			} else {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					schoolYears.forEach { year ->
						FilterChip(
							selected = year.name in selectedYears,
							onClick = { onYearToggled(year.name) },
							label = {
								Text(
									stringResource(
										R.string.teacher_absences_school_year,
										year.name,
										year.from,
										year.to
									)
								)
							},
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			}

			ListItem(
				headlineContent = { Text(stringResource(R.string.teacher_absences_custom_range)) },
				supportingContent = {
					Text(
						stringResource(
							R.string.infocenter_absences_timerange,
							from.format(dateFormatter),
							to.format(dateFormatter)
						)
					)
				},
				modifier = Modifier
					.fillMaxWidth()
					.clickable(onClick = onUseCustomRange)
			)

			if (customRange) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = onPickFrom) {
						Text(stringResource(R.string.teacher_absences_from, from.format(dateFormatter)))
					}
					TextButton(onClick = onPickTo) {
						Text(stringResource(R.string.teacher_absences_to, to.format(dateFormatter)))
					}
				}
				if (invalidRange) {
					Text(
						text = stringResource(R.string.teacher_absences_invalid_range),
						color = MaterialTheme.colorScheme.error
					)
				}
			} else if (selectedYears.isEmpty()) {
				Text(stringResource(R.string.teacher_absences_select_years))
			}

			if (tooManyYears) {
				Text(
					text = stringResource(
						R.string.teacher_absences_max_years,
						TEACHER_ABSENCES_MAX_YEARS
					),
					color = MaterialTheme.colorScheme.error
				)
			}
			if (reportError != null) {
				Text(
					text = stringResource(R.string.teacher_absences_error_report, reportError),
					color = MaterialTheme.colorScheme.error
				)
			}
			if (failedYears.isNotEmpty()) {
				Text(
					text = stringResource(
						R.string.teacher_absences_error_years,
						failedYears.joinToString(", ")
					),
					color = MaterialTheme.colorScheme.error
				)
			}

			Button(
				onClick = onRun,
				enabled = selectedTarget != null &&
					(customRange || selectedYears.isNotEmpty()) &&
					!loadingReport,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					if (loadingReport)
						stringResource(R.string.teacher_absences_running)
					else
						stringResource(R.string.teacher_absences_run)
				)
			}
		}
	}
}

private fun AnalyticsOptions.filteredTargets(
	mode: AnalyticsReportMode,
	query: String,
): List<AnalyticsTarget> {
	val term = query.trim()
	return targets
		.filter { mode.matches(it) }
		.filter {
			term.isBlank() ||
				it.name.contains(term, ignoreCase = true) ||
				it.id?.toString()?.contains(term) == true ||
				it.profileId?.contains(term, ignoreCase = true) == true
		}
}

@Composable
private fun ReportDashboard(entry: TeacherAbsencesReportEntry) {
	val report = entry.report
	val unknown = stringResource(R.string.teacher_absences_unknown)
	val from = report.from
	val to = report.to
	val tableFirst = from != null && from == to

	MetricCards(report = report)

	val range = if (from != null && to != null) {
		stringResource(
			R.string.teacher_absences_period,
			formatReportDate(from),
			formatReportDate(to)
		)
	} else {
		entry.label
	}
	val sourceValue = report.dataSource ?: report.source
	val source = if (sourceValue != null) {
		dataSourceLabel(sourceValue).takeIf { it.isNotBlank() } ?: unknown
	} else {
		unknown
	}
	val target = entry.targetName.ifBlank { unknown }

	Text(
		text = stringResource(R.string.teacher_absences_dashboard_scope, target, range),
		style = MaterialTheme.typography.bodyMedium
	)
	Text(
		text = stringResource(R.string.teacher_absences_dashboard_source, source),
		style = MaterialTheme.typography.bodySmall
	)
	Text(
		text = stringResource(
			R.string.teacher_absences_dashboard_coverage,
			stringResource(
				if (report.complete)
					R.string.teacher_absences_complete
				else
					R.string.teacher_absences_incomplete
			)
		),
		style = MaterialTheme.typography.bodySmall
	)

	val gaps = gapDates(report)
	if (gaps.isNotBlank()) {
		Text(
			text = stringResource(R.string.teacher_absences_gaps, gaps),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.error
		)
	}
	if (report.isReconstruction()) {
		Text(
			text = stringResource(R.string.teacher_absences_reconstruction_note),
			style = MaterialTheme.typography.bodySmall
		)
	}

	if (tableFirst) LessonChangesTable(report = report)

	DashboardSection(title = stringResource(R.string.teacher_absences_week_question)) {
		WeeklyChart(buckets = report.byWeek)
	}

	DashboardSection(title = stringResource(R.string.teacher_absences_missed_teacher_question)) {
		RankedChangeChart(
			items = report.missedTeachers,
			valueOf = { it.cancelled },
			detail = { item ->
				val scheduled = report.scheduledFor(item)
				if (scheduled != null) {
					stringResource(
						R.string.teacher_absences_chart_detail_scheduled,
						item.cancelled,
						scheduled
					)
				} else {
					stringResource(R.string.teacher_absences_chart_detail, item.cancelled)
				}
			}
		)
	}

	DashboardSection(title = stringResource(R.string.teacher_absences_subject_question)) {
		RankedChangeChart(
			items = report.missedSubjects,
			valueOf = { it.cancelled },
			detail = { item ->
				stringResource(
					R.string.teacher_absences_chart_detail_cancelled_of,
					item.cancelled,
					item.lessons
				)
			}
		)
	}

	DashboardSection(title = stringResource(R.string.teacher_absences_cover_question)) {
		RankedChangeChart(
			items = report.coverTeachers,
			valueOf = { it.covered },
			detail = { item ->
				stringResource(R.string.teacher_absences_chart_detail_covered, item.covered)
			}
		)
		Text(
			text = stringResource(R.string.teacher_absences_cover_note),
			style = MaterialTheme.typography.labelSmall
		)
	}

	ClashSection(report = report)
	Highlights(report = report)

	if (!tableFirst) LessonChangesTable(report = report)
}

@Composable
private fun MetricCards(report: AnalyticsReport) {
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		MetricCard(
			label = stringResource(R.string.teacher_absences_metric_total),
			value = (report.uniqueLessons ?: 0).toString(),
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			modifier = Modifier.weight(1f)
		)
		MetricCard(
			label = stringResource(R.string.teacher_absences_metric_cancelled),
			value = (report.cancelledLessons ?: 0).toString(),
			containerColor = MaterialTheme.colorScheme.secondaryContainer,
			modifier = Modifier.weight(1f)
		)
		MetricCard(
			label = stringResource(R.string.teacher_absences_metric_covered),
			value = (report.coveredLessons ?: 0).toString(),
			containerColor = MaterialTheme.colorScheme.tertiaryContainer,
			modifier = Modifier.weight(1f)
		)
	}
	Text(
		text = stringResource(R.string.teacher_absences_total_note),
		style = MaterialTheme.typography.labelSmall
	)
}

@Composable
private fun MetricCard(
	label: String,
	value: String,
	containerColor: Color,
	modifier: Modifier = Modifier,
) {
	Card(
		modifier = modifier,
		colors = CardDefaults.cardColors(containerColor = containerColor)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = value,
				style = MaterialTheme.typography.headlineMedium
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium
			)
		}
	}
}

@Composable
private fun DashboardSection(
	title: String,
	content: @Composable ColumnScope.() -> Unit,
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		)
	) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium
			)
			content()
		}
	}
}

@Composable
private fun WeeklyChart(buckets: List<AnalyticsBucket>) {
	if (buckets.isEmpty()) {
		Text(stringResource(R.string.teacher_absences_chart_empty))
		return
	}
	buckets.sortedBy { it.key }.forEach { bucket ->
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = bucket.key,
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.width(76.dp)
			)
			Column(modifier = Modifier.weight(1f)) {
				LinearProgressIndicator(
					progress = { progressFraction(bucket.cancelled, bucket.scheduled) },
					modifier = Modifier.fillMaxWidth()
				)
				Text(
					text = stringResource(
						R.string.teacher_absences_week_detail,
						bucket.cancelled,
						bucket.scheduled
					),
					style = MaterialTheme.typography.labelSmall
				)
			}
		}
	}
}

@Composable
private fun RankedChangeChart(
	items: List<AnalyticsChangeCount>,
	valueOf: (AnalyticsChangeCount) -> Int,
	detail: @Composable (AnalyticsChangeCount) -> String,
) {
	val unknown = stringResource(R.string.teacher_absences_unknown)
	if (items.isEmpty()) {
		Text(stringResource(R.string.teacher_absences_chart_empty))
		return
	}
	val sorted = items.sortedByDescending(valueOf)
	val max = sorted.firstOrNull()?.let(valueOf)?.takeIf { it > 0 } ?: 1
	sorted.forEach { item ->
		Column(modifier = Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = item.name.ifBlank { unknown },
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.weight(1f)
				)
				Text(
					text = valueOf(item).toString(),
					style = MaterialTheme.typography.labelMedium
				)
			}
			LinearProgressIndicator(
				progress = { progressFraction(valueOf(item), max) },
				modifier = Modifier.fillMaxWidth()
			)
			Text(
				text = detail(item),
				style = MaterialTheme.typography.labelSmall
			)
		}
	}
	val topCount = sorted.firstOrNull()?.let(valueOf) ?: 0
	if (topCount > 0) {
		val tied = sorted.filter { valueOf(it) == topCount }
		if (tied.size > 1) {
			Text(
				text = stringResource(
					R.string.teacher_absences_tie_for_most,
					tied.joinToString(", ") { it.name.ifBlank { unknown } },
					topCount
				),
				style = MaterialTheme.typography.labelSmall
			)
		}
	}
}

@Composable
private fun ClashSection(report: AnalyticsReport) {
	val unknown = stringResource(R.string.teacher_absences_unknown)
	val count = report.clashingLessons
		?: report.lessonChanges.count { !it.clash.isNullOrBlank() }
	val clashes = report.lessonChanges
		.filter { !it.clash.isNullOrBlank() }
		.sortedWith(lessonChangeComparator)

	DashboardSection(title = stringResource(R.string.teacher_absences_clash_question)) {
		Text(
			text = stringResource(R.string.teacher_absences_clash_count, count),
			style = MaterialTheme.typography.bodySmall
		)
		if (clashes.isEmpty()) {
			Text(stringResource(R.string.teacher_absences_clash_none))
		} else {
			clashes.forEach { change ->
				Text(
					text = stringResource(
						R.string.teacher_absences_clash_item,
						formatReportDate(change.date),
						timeRange(change, unknown),
						change.subjects.labels().ifBlank { unknown },
						change.clash ?: unknown
					),
					style = MaterialTheme.typography.bodySmall
				)
			}
		}
	}
}

@Composable
private fun Highlights(report: AnalyticsReport) {
	val unknown = stringResource(R.string.teacher_absences_unknown)
	val parts = mutableListOf<String>()

	report.topWeek()?.let { week ->
		parts += stringResource(
			R.string.teacher_absences_highlight_week,
			week.key,
			week.cancelled,
			week.scheduled
		)
	}
	report.missedSubjects.topBy { it.cancelled }?.let { subject ->
		parts += stringResource(
			R.string.teacher_absences_highlight_subject,
			subject.name.ifBlank { unknown },
			subject.cancelled
		)
	}
	report.missedTeachers.topBy { it.cancelled }?.let { teacher ->
		parts += stringResource(
			R.string.teacher_absences_highlight_teacher,
			teacher.name.ifBlank { unknown },
			teacher.cancelled
		)
	}
	report.coverTeachers.topBy { it.covered }?.let { teacher ->
		parts += stringResource(
			R.string.teacher_absences_highlight_cover,
			teacher.name.ifBlank { unknown },
			teacher.covered
		)
	}
	val gaps = gapDates(report)
	if (gaps.isNotBlank()) {
		parts += stringResource(R.string.teacher_absences_highlight_gaps, gaps)
	}
	if (report.warnings.isNotEmpty()) {
		parts += stringResource(
			R.string.teacher_absences_highlight_notes,
			report.warnings.joinToString("; ")
		)
	}

	DashboardSection(title = stringResource(R.string.teacher_absences_highlights)) {
		Text(
			text = parts.joinToString(" ").ifBlank {
				stringResource(R.string.teacher_absences_highlights_none)
			},
			style = MaterialTheme.typography.bodyMedium
		)
	}
}

@Composable
private fun LessonChangesTable(report: AnalyticsReport) {
	val unknown = stringResource(R.string.teacher_absences_unknown)
	Text(
		text = stringResource(R.string.teacher_absences_lesson_changes),
		style = MaterialTheme.typography.titleMedium
	)
	if (report.lessonChanges.isEmpty()) {
		Text(stringResource(R.string.teacher_absences_no_lesson_changes))
		return
	}

	val changes = report.lessonChanges.sortedWith(lessonChangeComparator)
	val scrollState = rememberScrollState()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(scrollState)
	) {
		TableHeader(stringResource(R.string.teacher_absences_column_date), 100.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_time), 88.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_subjects), 140.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_original_teacher), 150.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_what_changed), 150.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_cover_teacher), 140.dp)
		TableHeader(stringResource(R.string.teacher_absences_column_clash), 140.dp)
	}
	changes.forEach { change ->
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(scrollState)
		) {
			TableCell(formatReportDate(change.date), 100.dp)
			TableCell(timeRange(change, unknown), 88.dp)
			TableCell(change.subjects.labels().ifBlank { unknown }, 140.dp)
			TableCell(change.missedTeachers.labels().ifBlank { unknown }, 150.dp)
			TableCell(whatChanged(change), 150.dp)
			TableCell(
				change.covers.joinToString(", ") { it.teacher.name.ifBlank { unknown } }
					.ifBlank { unknown },
				140.dp
			)
			TableCell(change.clash?.takeIf { it.isNotBlank() } ?: unknown, 140.dp)
		}
	}
}

@Composable
private fun whatChanged(change: AnalyticsLessonChange): String =
	when {
		change.cancelled && change.covers.isNotEmpty() ->
			stringResource(R.string.teacher_absences_change_cancelled_cover)
		change.cancelled -> stringResource(R.string.teacher_absences_change_cancelled)
		change.covers.isNotEmpty() -> stringResource(R.string.teacher_absences_change_cover)
		else -> stringResource(R.string.teacher_absences_change_other)
	}

@Composable
private fun TableHeader(text: String, width: Dp) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelMedium,
		modifier = Modifier.width(width).padding(4.dp)
	)
}

@Composable
private fun TableCell(text: String, width: Dp) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodySmall,
		modifier = Modifier.width(width).padding(4.dp)
	)
}

private val lessonChangeComparator =
	compareBy<AnalyticsLessonChange> { it.date }
		.thenBy { it.start.orEmpty() }

private fun List<AnalyticsElementReference>.labels(): String =
	joinToString(", ") { it.name }

private fun timeRange(
	change: AnalyticsLessonChange,
	unknown: String,
): String {
	val times = listOfNotNull(
		change.start?.takeIf { it.isNotBlank() },
		change.end?.takeIf { it.isNotBlank() }
	)
	return if (times.isEmpty()) unknown else times.joinToString("–")
}

private fun progressFraction(value: Int, total: Int): Float =
	if (total <= 0) 0f else (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)

private fun AnalyticsReport.isReconstruction(): Boolean =
	dataSource == "reconstruction" || source == "reconstruction"

private fun AnalyticsReport.scheduledFor(item: AnalyticsChangeCount): Int? =
	teachers.firstOrNull { teacher ->
		(item.id != null && teacher.id == item.id) || teacher.name == item.name
	}?.scheduled?.takeIf { it > 0 }

private fun AnalyticsReport.topWeek(): AnalyticsBucket? =
	byWeek.filter { it.cancelled > 0 }.maxByOrNull { it.cancelled }

private fun List<AnalyticsChangeCount>.topBy(valueOf: (AnalyticsChangeCount) -> Int): AnalyticsChangeCount? =
	filter { valueOf(it) > 0 }.maxByOrNull(valueOf)

private fun gapDates(report: AnalyticsReport): String =
	report.windows
		.filter { it.status != "observed" }
		.joinToString(", ") { window ->
			"${formatReportDate(window.from)} – ${formatReportDate(window.to)} (${window.status})"
		}

private fun formatReportDate(value: String): String =
	runCatching { LocalDate.parse(value).format(dateFormatter) }.getOrDefault(value)

@Composable
private fun dataSourceLabel(source: String): String = when (source) {
	"personal" -> stringResource(R.string.teacher_absences_source_personal)
	"own-teacher" -> stringResource(R.string.teacher_absences_source_own_teacher)
	"saved-teacher" -> stringResource(R.string.teacher_absences_source_saved_teacher)
	"shared-class" -> stringResource(R.string.teacher_absences_source_shared_class)
	"reconstruction" -> stringResource(R.string.teacher_absences_source_reconstruction)
	"teacher" -> stringResource(R.string.teacher_absences_source_teacher)
	"class", "class-history" -> stringResource(R.string.teacher_absences_source_class)
	"room" -> stringResource(R.string.teacher_absences_source_room)
	else -> source
}

private val dateFormatter: DateTimeFormatter =
	DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
