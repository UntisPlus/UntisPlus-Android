package com.deelabs.untisplus.ui.pages.teacherabsences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.AnalyticsOptions
import com.deelabs.untisplus.api.model.proxy.AnalyticsReport
import com.deelabs.untisplus.api.model.proxy.AnalyticsReportRequest
import com.deelabs.untisplus.api.model.proxy.AnalyticsTarget
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.ui.navigation.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val TEACHER_ABSENCES_MAX_YEARS = 6

enum class AnalyticsReportMode(val apiValue: String, val labelRes: Int) {
	TEACHER("teacher", R.string.all_teachers),
	PERSONAL("personal", R.string.all_personal_timetable),
	CLASS("class", R.string.all_classes),
	ROOM("room", R.string.all_rooms);

	fun matches(target: AnalyticsTarget): Boolean = when (this) {
		TEACHER -> target.mode == "teacher"
		PERSONAL -> target.mode == "personal"
		CLASS -> target.mode == "class" || target.mode == "class-history"
		ROOM -> target.mode == "room"
	}
}

data class TeacherAbsencesReportEntry(
	val label: String,
	val targetName: String,
	val report: AnalyticsReport,
)

/**
 * A selectable school-year period. Untis master-data years are the official
 * source and are sent as an explicit from/to range because the proxy does not
 * know those year names. Years configured only on the proxy are sent as
 * `schoolYear` as a fallback.
 */
data class TeacherAbsencesYear(
	val name: String,
	val from: String,
	val to: String,
	val configuredOnApi: Boolean,
)

internal fun SchoolYearEntity.toTeacherAbsencesYear() = TeacherAbsencesYear(
	name = name,
	from = startDate.toString(),
	to = endDate.toString(),
	configuredOnApi = false,
)

internal fun List<TeacherAbsencesYear>.defaultYear(today: LocalDate): TeacherAbsencesYear? =
	firstOrNull { it.contains(today) }
		?: filter { LocalDate.parse(it.from) <= today }.maxByOrNull { LocalDate.parse(it.from) }
		?: maxByOrNull { LocalDate.parse(it.from) }

internal fun TeacherAbsencesYear.contains(date: LocalDate): Boolean =
	LocalDate.parse(from) <= date && date <= LocalDate.parse(to)

data class TeacherAbsencesUiState(
	val supported: Boolean? = null,
	val loadingOptions: Boolean = true,
	val optionsError: Boolean = false,
	val options: AnalyticsOptions? = null,
	val selectedMode: AnalyticsReportMode? = null,
	val searchQuery: String = "",
	val selectedTarget: AnalyticsTarget? = null,
	val selectedDataSource: String? = null,
	val schoolYears: List<TeacherAbsencesYear> = emptyList(),
	val selectedYears: Set<String> = emptySet(),
	val customRange: Boolean = false,
	val from: LocalDate = LocalDate.now(),
	val to: LocalDate = LocalDate.now(),
	val loadingReport: Boolean = false,
	val reportError: String? = null,
	val failedYears: List<String> = emptyList(),
	val invalidRange: Boolean = false,
	val tooManyYears: Boolean = false,
	val reports: List<TeacherAbsencesReportEntry> = emptyList(),
)

class TeacherAbsencesViewModel constructor(
	private val proxyRepository: ProxyRepository,
	private val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
	clock: Clock,
) : ViewModel() {
	private val today = LocalDate.now(clock)

	private val _state = MutableStateFlow(
		TeacherAbsencesUiState(
			from = today.minusDays(7),
			to = today.minusDays(1),
		)
	)
	val state: StateFlow<TeacherAbsencesUiState> = _state

	init {
		viewModelScope.launch {
			val supported = proxyRepository.isTeacherAbsencesSupported()
			_state.update { it.copy(supported = supported) }
			if (supported) loadOptions() else _state.update { it.copy(loadingOptions = false) }
		}
	}

	private suspend fun loadOptions() {
		_state.update { it.copy(loadingOptions = true, optionsError = false) }
		runCatching { proxyRepository.analyticsOptions() }
			.onSuccess { options ->
				val masterYears = masterDataRepository.schoolYears()
					.map { it.toTeacherAbsencesYear() }
				val apiYears = options.schoolYears.map { year ->
					TeacherAbsencesYear(year.name, year.from, year.to, configuredOnApi = true)
				}
				val years = (masterYears + apiYears).distinctBy { it.name }
				val defaultYear = years.defaultYear(today)
				val defaultFrom = defaultYear?.from?.let(LocalDate::parse)
				val defaultTo = defaultYear?.to?.let(LocalDate::parse)
					?.coerceAtMost(today.minusDays(1))
					?.takeIf { defaultFrom == null || !it.isBefore(defaultFrom) }
				_state.update {
					it.copy(
						loadingOptions = false,
						options = options,
						schoolYears = years,
						selectedMode = null,
						selectedTarget = null,
						selectedDataSource = null,
						selectedYears = defaultYear?.name?.let(::setOf).orEmpty(),
						customRange = false,
						from = defaultFrom ?: it.from,
						to = defaultTo ?: it.to,
						searchQuery = "",
						reports = emptyList(),
					)
				}
			}
			.onFailure {
				_state.update { it.copy(loadingOptions = false, optionsError = true) }
			}
	}

	fun retryOptions() = viewModelScope.launch { loadOptions() }

	fun selectMode(mode: AnalyticsReportMode) {
		_state.update {
			it.copy(
				selectedMode = mode,
				selectedTarget = null,
				selectedDataSource = null,
				searchQuery = "",
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
				tooManyYears = false,
			)
		}
	}

	fun setSearchQuery(query: String) {
		_state.update { it.copy(searchQuery = query) }
	}

	fun selectTarget(target: AnalyticsTarget) {
		_state.update {
			it.copy(
				selectedTarget = target,
				selectedDataSource = null,
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
			)
		}
	}

	fun clearTarget() {
		_state.update {
			it.copy(
				selectedTarget = null,
				selectedDataSource = null,
				searchQuery = "",
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
			)
		}
	}

	fun selectDataSource(source: String?) {
		_state.update {
			it.copy(
				selectedDataSource = source,
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
			)
		}
	}

	fun toggleYear(name: String) {
		_state.update { state ->
			val selected = state.selectedYears
			when {
				name in selected -> state.copy(
					selectedYears = selected - name,
					reports = emptyList(),
					reportError = null,
					failedYears = emptyList(),
					tooManyYears = false,
					customRange = false,
				)
				selected.size >= TEACHER_ABSENCES_MAX_YEARS -> state.copy(tooManyYears = true)
				else -> state.copy(
					selectedYears = selected + name,
					reports = emptyList(),
					reportError = null,
					failedYears = emptyList(),
					tooManyYears = false,
					customRange = false,
				)
			}
		}
	}

	fun useSchoolYears() {
		_state.update {
			it.copy(
				customRange = false,
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
			)
		}
	}

	fun useCustomRange() {
		_state.update {
			it.copy(
				customRange = true,
				reports = emptyList(),
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
			)
		}
	}

	fun setFrom(date: LocalDate) {
		_state.update { it.copy(from = date, invalidRange = false, reports = emptyList()) }
	}

	fun setTo(date: LocalDate) {
		_state.update { it.copy(to = date, invalidRange = false, reports = emptyList()) }
	}

	fun runReport() = viewModelScope.launch {
		val state = _state.value
		val target = state.selectedTarget ?: return@launch

		_state.update {
			it.copy(
				loadingReport = true,
				reportError = null,
				failedYears = emptyList(),
				invalidRange = false,
				reports = emptyList(),
			)
		}

		if (state.customRange) {
			if (state.from.isAfter(state.to) || ChronoUnit.DAYS.between(state.from, state.to) > 365) {
				_state.update { it.copy(loadingReport = false, invalidRange = true) }
				return@launch
			}
			val request = requestFor(
				target = target,
				from = state.from.toString(),
				to = state.to.toString(),
				dataSource = state.selectedDataSource,
			) ?: run {
				_state.update { it.copy(loadingReport = false, reportError = "Unsupported report target") }
				return@launch
			}
			runCatching { proxyRepository.teacherCancellations(request) }
				.onSuccess { report ->
					_state.update {
						it.copy(
							loadingReport = false,
							reports = listOf(
								TeacherAbsencesReportEntry(
									label = "${state.from} – ${state.to}",
									targetName = target.name,
									report = report
								)
							),
						)
					}
				}
				.onFailure { error ->
					_state.update {
						it.copy(loadingReport = false, reportError = error.message ?: error::class.simpleName)
					}
				}
			return@launch
		}

		val years = state.schoolYears.filter { it.name in state.selectedYears }
		if (years.isEmpty()) {
			_state.update { it.copy(loadingReport = false) }
			return@launch
		}

		val entries = mutableListOf<TeacherAbsencesReportEntry>()
		val failed = mutableListOf<String>()
		for (year in years) {
			val request = requestFor(
				target = target,
				from = year.from.takeIf { !year.configuredOnApi },
				to = year.to.takeIf { !year.configuredOnApi },
				schoolYear = year.name.takeIf { year.configuredOnApi },
				dataSource = state.selectedDataSource,
			) ?: continue
			runCatching { proxyRepository.teacherCancellations(request) }
				.onSuccess { report ->
					entries += TeacherAbsencesReportEntry(
						label = year.name,
						targetName = target.name,
						report = report,
					)
				}
				.onFailure { failed += year.name }
		}
		_state.update {
			it.copy(
				loadingReport = false,
				reports = entries,
				failedYears = failed,
			)
		}
	}

	private fun requestFor(
		target: AnalyticsTarget,
		from: String? = null,
		to: String? = null,
		schoolYear: String? = null,
		dataSource: String? = null,
	): AnalyticsReportRequest? = when (target.mode) {
		"personal" -> AnalyticsReportRequest(
			mode = "personal",
			from = from,
			to = to,
			schoolYear = schoolYear,
			dataSource = dataSource,
		)

		"teacher" -> target.id?.let {
			AnalyticsReportRequest(
				mode = "teacher",
				teacherId = it,
				from = from,
				to = to,
				schoolYear = schoolYear,
				dataSource = dataSource,
			)
		}

		"room" -> {
			val room = target.name.ifBlank { target.id?.toString().orEmpty() }
			room.takeIf { it.isNotBlank() }?.let {
				AnalyticsReportRequest(
					mode = "room",
					room = it,
					from = from,
					to = to,
					schoolYear = schoolYear,
					dataSource = dataSource,
				)
			}
		}

		"class" -> target.id?.let {
			AnalyticsReportRequest(
				mode = "class",
				classId = it,
				from = from,
				to = to,
				schoolYear = schoolYear,
				dataSource = dataSource,
			)
		}

		"class-history" -> target.profileId?.let {
			AnalyticsReportRequest(
				mode = "class-history",
				profileId = it,
				from = from,
				to = to,
				schoolYear = schoolYear,
				dataSource = dataSource,
			)
		}

		else -> null
	}

	fun backToSetup() {
		_state.update { it.copy(reports = emptyList(), reportError = null, failedYears = emptyList()) }
	}

	fun goBack() {
		navigator.popBackStack()
	}
}
