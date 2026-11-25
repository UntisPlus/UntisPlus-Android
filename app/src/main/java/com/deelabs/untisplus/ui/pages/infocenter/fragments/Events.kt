package com.deelabs.untisplus.ui.pages.infocenter.fragments

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.repository.LocalMasterDataRepository
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InfoCenterEvents(
	uiState: EventsUiState,
	children: List<Person> = emptyList(),
	selectedChildId: Long? = null,
	onSelectChild: (Long?) -> Unit = {},
	proxyAvailable: Boolean = false,
	onSetHomeworkDone: (HomeWork, Boolean) -> Unit = { _, _ -> },
) {
	Column(modifier = Modifier.fillMaxSize()) {
		InfoCenterChildTabs(children, selectedChildId, onSelectChild)

		Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
			Crossfade(targetState = uiState, label = "InfoCenter Events Content") { state ->
				LazyColumn(
					horizontalAlignment = Alignment.CenterHorizontally,
					modifier = Modifier.fillMaxSize()
				) {
					item {
						Text(
							text = stringResource(R.string.infocenter_exams),
							style = MaterialTheme.typography.labelLarge,
							modifier = Modifier.padding(bottom = 8.dp)
						)
					}

					when (state) {
						EventsUiState.Loading -> item { InfoCenterLoading() }
						is EventsUiState.Success -> {
							state.exams.fold(
								onSuccess = { exams ->
									if (state.isExamsEmpty) item {
										Text(
											text = stringResource(R.string.infocenter_exams_empty),
											textAlign = TextAlign.Center,
											modifier = Modifier.fillMaxWidth()
										)
									} else {
										items(exams) { entry ->
											ExamItem(entry.item, entry.studentName)
										}
									}
								},
								onFailure = { item { InfoCenterError(it) } }
							)
						}
					}

					item {
						Text(
							text = stringResource(R.string.infocenter_homework),
							style = MaterialTheme.typography.labelLarge,
							modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
						)
					}

					when (state) {
						EventsUiState.Loading -> item { InfoCenterLoading() }
						is EventsUiState.Success -> {
							state.homework.fold(
								onSuccess = { homework ->
									if (state.isHomeworkEmpty) item {
										Text(
											text = stringResource(R.string.infocenter_homework_empty),
											textAlign = TextAlign.Center,
											modifier = Modifier.fillMaxWidth()
										)
									} else {
										items(homework) { entry ->
											HomeworkItem(
												item = entry.item,
												studentName = entry.studentName,
												toggleable = proxyAvailable,
												onSetDone = { done -> onSetHomeworkDone(entry.item, done) }
											)
										}
									}
								},
								onFailure = { item { InfoCenterError(it) } }
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ExamItem(item: Exam, studentName: String? = null) {
	val subject = LocalMasterDataRepository.current.getShortName(
		item.subjectId,
		ElementType.SUBJECT
	)
	val title = if (!item.name.contains(subject)) stringResource(
		R.string.infocenter_events_exam_name_long,
		subject,
		item.name
	) else item.name
	ListItem(
		overlineContent = {
			Text(withStudent(studentName, formatExamTime(item.startDateTime.toJavaLocalDateTime(), item.endDateTime.toJavaLocalDateTime())))
		},
		headlineContent = { Text(withStudent(studentName, title)) }
	)
}

@Composable
private fun HomeworkItem(
	item: HomeWork,
	studentName: String? = null,
	toggleable: Boolean,
	onSetDone: (Boolean) -> Unit
) {
	ListItem(
		overlineContent = {
			Text(withStudent(studentName, item.endDate.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))))
		},
		headlineContent = {
			/* TODO Text(
				LocalMasterDataRepository.current.getLongName(
					item.lessonsById[item.homework.lessonId.toString()]?.subjectId ?: 0,
					ElementType.SUBJECT
				)
			)*/
		},
		supportingContent = if (item.text.isNotBlank()) {
			{
				Text(
					item.text,
					style = if (item.done)
						MaterialTheme.typography.bodyMedium.copy(
							textDecoration = TextDecoration.LineThrough
						)
					else MaterialTheme.typography.bodyMedium
				)
			}
		} else null,
		trailingContent = if (toggleable) {
			{
				Checkbox(
					checked = item.done,
					onCheckedChange = onSetDone
				)
			}
		} else null
	)
}

@Composable
private fun withStudent(studentName: String?, content: String): String =
	if (studentName.isNullOrBlank()) content else stringResource(R.string.parent_event_student, studentName, content)

@Composable
private fun formatExamTime(startDateTime: LocalDateTime, endDateTime: LocalDateTime): String {
	return stringResource(
		if (startDateTime.dayOfYear == endDateTime.dayOfYear)
			R.string.infocenter_timeformat_sameday
		else
			R.string.infocenter_timeformat,
		startDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
		startDateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
		endDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
		endDateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
	)
}

data class EventItem<T>(
	val item: T,
	val studentName: String? = null
)

sealed interface EventsUiState {
	data object Loading : EventsUiState

	data class Success(
		val exams: Result<List<EventItem<Exam>>>,
		val homework: Result<List<EventItem<HomeWork>>>
	) : EventsUiState {
		val isExamsEmpty: Boolean get() = exams.getOrDefault(emptyList()).isEmpty()
		val isHomeworkEmpty: Boolean get() = homework.getOrDefault(emptyList()).isEmpty()
	}
}
