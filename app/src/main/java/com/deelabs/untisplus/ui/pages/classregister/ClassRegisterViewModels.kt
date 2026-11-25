package com.deelabs.untisplus.ui.pages.classregister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.data.repository.ClassRegisterEntry
import com.deelabs.untisplus.data.repository.ClassRegisterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.StudentAbsenceEntry
import com.deelabs.untisplus.ui.navigation.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A school year offered as a ready-made date range. */
data class SchoolYearOption(
	val name: String,
	val from: LocalDate,
	val to: LocalDate,
)

data class TeacherListUiState<T>(
	val from: LocalDate = LocalDate.now().minusDays(7),
	val to: LocalDate = LocalDate.now().minusDays(1),
	val searchQuery: String = "",
	val filter: String? = null,
	val selectedYear: String? = null,
	val schoolYears: List<SchoolYearOption> = emptyList(),
	val loading: Boolean = true,
	val error: String? = null,
	val entries: List<T> = emptyList(),
)

private fun LocalDate.toApiDate() = kotlinx.datetime.LocalDate.parse(toString())

private fun invalidRange(from: LocalDate, to: LocalDate): Boolean = to.isBefore(from)

private suspend fun loadSchoolYears(
	masterDataRepository: MasterDataRepository,
): List<SchoolYearOption> = runCatching { masterDataRepository.schoolYears() }
	.getOrDefault(emptyList())
	.sortedBy { it.startDate }
	.takeLast(6)
	.map {
		SchoolYearOption(
			name = it.name,
			from = LocalDate.parse(it.startDate.toString()),
			to = LocalDate.parse(it.endDate.toString()),
		)
	}

class StudentAbsencesViewModel constructor(
	private val repository: ClassRegisterRepository,
	private val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
) : ViewModel() {
	private val _state = MutableStateFlow(TeacherListUiState<StudentAbsenceEntry>())
	val state: StateFlow<TeacherListUiState<StudentAbsenceEntry>> = _state

	init {
		viewModelScope.launch {
			_state.update { it.copy(schoolYears = loadSchoolYears(masterDataRepository)) }
		}
		reload()
	}

	fun reload() = viewModelScope.launch {
		val current = _state.value
		if (invalidRange(current.from, current.to)) {
			_state.update { it.copy(loading = false, entries = emptyList(), error = INVALID_RANGE) }
			return@launch
		}
		_state.update { it.copy(loading = true, error = null) }
		repository.loadAbsences(current.from.toApiDate(), current.to.toApiDate())
			.onSuccess { entries -> _state.update { it.copy(loading = false, entries = entries) } }
			.onFailure { error -> _state.update { it.copy(loading = false, error = error.message ?: UNKNOWN) } }
	}

	fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

	fun setFilter(filter: String?) = _state.update { it.copy(filter = filter) }

	fun selectYear(name: String?) {
		val year = _state.value.schoolYears.firstOrNull { it.name == name }
		_state.update {
			it.copy(
				selectedYear = if (year != null) name else null,
				from = year?.from ?: it.from,
				to = year?.to ?: it.to,
			)
		}
		reload()
	}

	fun setFrom(date: LocalDate) {
		_state.update { it.copy(from = date, selectedYear = null) }
		reload()
	}

	fun setTo(date: LocalDate) {
		_state.update { it.copy(to = date, selectedYear = null) }
		reload()
	}

	fun goBack() = navigator.popBackStack()

	private companion object {
		const val INVALID_RANGE = "End date must be on or after the start date."
		const val UNKNOWN = "Unknown error"
	}
}

class ClassRegisterViewModel constructor(
	private val repository: ClassRegisterRepository,
	private val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
) : ViewModel() {
	private val _state = MutableStateFlow(TeacherListUiState<ClassRegisterEntry>())
	val state: StateFlow<TeacherListUiState<ClassRegisterEntry>> = _state

	init {
		viewModelScope.launch {
			_state.update { it.copy(schoolYears = loadSchoolYears(masterDataRepository)) }
		}
		reload()
	}

	fun reload() = viewModelScope.launch {
		val current = _state.value
		if (invalidRange(current.from, current.to)) {
			_state.update { it.copy(loading = false, entries = emptyList(), error = INVALID_RANGE) }
			return@launch
		}
		_state.update { it.copy(loading = true, error = null) }
		repository.loadEvents(current.from.toApiDate(), current.to.toApiDate())
			.onSuccess { entries -> _state.update { it.copy(loading = false, entries = entries) } }
			.onFailure { error -> _state.update { it.copy(loading = false, error = error.message ?: UNKNOWN) } }
	}

	fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

	fun setFilter(filter: String?) = _state.update { it.copy(filter = filter) }

	fun selectYear(name: String?) {
		val year = _state.value.schoolYears.firstOrNull { it.name == name }
		_state.update {
			it.copy(
				selectedYear = if (year != null) name else null,
				from = year?.from ?: it.from,
				to = year?.to ?: it.to,
			)
		}
		reload()
	}

	fun setFrom(date: LocalDate) {
		_state.update { it.copy(from = date, selectedYear = null) }
		reload()
	}

	fun setTo(date: LocalDate) {
		_state.update { it.copy(to = date, selectedYear = null) }
		reload()
	}

	fun goBack() = navigator.popBackStack()

	private companion object {
		const val INVALID_RANGE = "End date must be on or after the start date."
		const val UNKNOWN = "Unknown error"
	}
}
