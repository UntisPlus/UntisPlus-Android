package com.deelabs.untisplus.ui.pages.classregister

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.repository.ClassRegisterEntry
import com.deelabs.untisplus.data.repository.StudentAbsenceEntry
import com.deelabs.untisplus.ui.common.AppScaffold
import com.deelabs.untisplus.ui.dialogs.DatePickerDialog
import com.deelabs.untisplus.ui.functional.bottomInsets
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

@Composable
fun StudentAbsences(
	viewModel: StudentAbsencesViewModel = koinViewModel()
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	TeacherListScreen(
		title = stringResource(R.string.activity_title_student_absences),
		state = state,
		onBack = viewModel::goBack,
		onFrom = viewModel::setFrom,
		onTo = viewModel::setTo,
		onReload = viewModel::reload,
		onSearchQueryChange = viewModel::setSearchQuery,
		onFilterChange = viewModel::setFilter,
		onSelectYear = viewModel::selectYear,
		filterOptions = listOf(
			stringResource(R.string.class_register_all) to null,
			stringResource(R.string.class_register_excused) to EXCUSED,
			stringResource(R.string.class_register_unexcused) to UNEXCUSED,
		),
		matches = { absence, query, filter ->
			val matchesQuery = query.isBlank() ||
				listOf(absence.studentName, absence.reason, absence.text)
					.any { it.contains(query, ignoreCase = true) }
			val matchesFilter = when (filter) {
				EXCUSED -> absence.excused
				UNEXCUSED -> !absence.excused
				else -> true
			}
			matchesQuery && matchesFilter
		},
	) { absence -> AbsenceRow(absence) }
}

@Composable
fun ClassRegister(
	viewModel: ClassRegisterViewModel = koinViewModel()
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val allLabel = stringResource(R.string.class_register_all)
	val lessonFilters = remember(state.entries, allLabel) {
		buildList {
			add(allLabel to null)
			state.entries
				.map { it.lesson }
				.filter { it.isNotBlank() }
				.distinct()
				.take(10)
				.forEach { add(it to it) }
		}
	}
	TeacherListScreen(
		title = stringResource(R.string.activity_title_class_register),
		state = state,
		onBack = viewModel::goBack,
		onFrom = viewModel::setFrom,
		onTo = viewModel::setTo,
		onReload = viewModel::reload,
		onSearchQueryChange = viewModel::setSearchQuery,
		onFilterChange = viewModel::setFilter,
		onSelectYear = viewModel::selectYear,
		filterOptions = lessonFilters,
		matches = { event, query, filter ->
			val matchesQuery = query.isBlank() ||
				listOf(event.lesson, event.reason, event.text)
					.any { it.contains(query, ignoreCase = true) }
			val matchesFilter = filter == null || event.lesson == filter
			matchesQuery && matchesFilter
		},
	) { event -> ClassRegisterRow(event) }
}

@Composable
private fun AbsenceRow(entry: StudentAbsenceEntry) {
	ListItem(
		headlineContent = {
			Text(
				stringResource(
					R.string.class_register_student_absence,
					entry.studentName,
					entry.date,
					entry.time,
				)
			)
		},
		supportingContent = {
			Text(
				listOfNotNull(
					entry.reason.takeIf { it.isNotBlank() },
					entry.text.takeIf { it.isNotBlank() },
					stringResource(
						if (entry.excused) R.string.class_register_excused
						else R.string.class_register_unexcused
					),
				).joinToString(" · ")
			)
		},
	)
}

@Composable
private fun ClassRegisterRow(entry: ClassRegisterEntry) {
	ListItem(
		headlineContent = {
			Text(
				stringResource(
					R.string.class_register_event,
					entry.date,
					entry.time,
					entry.lesson,
				)
			)
		},
		supportingContent = {
			Text(
				listOfNotNull(
					entry.reason.takeIf { it.isNotBlank() },
					entry.text.takeIf { it.isNotBlank() },
				).joinToString(" · ")
			)
		},
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> TeacherListScreen(
	title: String,
	state: TeacherListUiState<T>,
	onBack: () -> Unit,
	onFrom: (LocalDate) -> Unit,
	onTo: (LocalDate) -> Unit,
	onReload: () -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onFilterChange: (String?) -> Unit,
	onSelectYear: (String?) -> Unit,
	filterOptions: List<Pair<String, String?>>,
	matches: (T, String, String?) -> Boolean,
	entryContent: @Composable (T) -> Unit,
) {
	var pickingFrom by remember { mutableStateOf(false) }
	var pickingTo by remember { mutableStateOf(false) }
	val query = state.searchQuery.trim()
	val visibleEntries = remember(state.entries, query, state.filter) {
		state.entries.filter { matches(it, query, state.filter) }
	}

	AppScaffold(
		modifier = Modifier.bottomInsets(),
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(title) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
							contentDescription = stringResource(R.string.all_back),
						)
					}
				},
			)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
		) {
			Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
				TextButton(onClick = { pickingFrom = true }) {
					Text(stringResource(R.string.teacher_absences_from, state.from.toString()))
				}
				TextButton(onClick = { pickingTo = true }) {
					Text(stringResource(R.string.teacher_absences_to, state.to.toString()))
				}
			}

			if (state.schoolYears.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState())
						.padding(horizontal = 16.dp)
				) {
					FilterChip(
						selected = state.selectedYear == null,
						onClick = { onSelectYear(null) },
						label = { Text(stringResource(R.string.class_register_custom_range)) },
					)
					state.schoolYears.forEach { year ->
						FilterChip(
							selected = state.selectedYear == year.name,
							onClick = { onSelectYear(year.name) },
							label = { Text(year.name) },
						)
					}
				}
			}

			OutlinedTextField(
				value = state.searchQuery,
				onValueChange = onSearchQueryChange,
				label = { Text(stringResource(R.string.class_register_search)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				filterOptions.forEach { (label, value) ->
					FilterChip(
						selected = state.filter == value,
						onClick = { onFilterChange(value) },
						label = { Text(label) },
					)
				}
			}

			when {
				state.loading -> Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator()
				}

				state.error != null -> Column(
					modifier = Modifier.padding(16.dp),
				) {
					Text(stringResource(R.string.class_register_error, state.error.orEmpty()))
					Button(onClick = onReload) {
						Text(stringResource(R.string.class_register_retry))
					}
				}

				visibleEntries.isEmpty() -> Text(
					text = stringResource(R.string.class_register_no_entries),
					modifier = Modifier.padding(16.dp),
				)

				else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
					items(visibleEntries) { entry -> entryContent(entry) }
				}
			}
		}
	}

	if (pickingFrom) {
		DatePickerDialog(
			initialSelection = state.from,
			onDismiss = { pickingFrom = false },
			onDateSelected = {
				onFrom(it)
				pickingFrom = false
			},
		)
	}
	if (pickingTo) {
		DatePickerDialog(
			initialSelection = state.to,
			onDismiss = { pickingTo = false },
			onDateSelected = {
				onTo(it)
				pickingTo = false
			},
		)
	}
}

private const val EXCUSED = "excused"
private const val UNEXCUSED = "unexcused"
