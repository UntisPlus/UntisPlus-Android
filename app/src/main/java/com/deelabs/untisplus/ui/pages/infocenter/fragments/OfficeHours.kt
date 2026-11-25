package com.deelabs.untisplus.ui.pages.infocenter.fragments

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.response.OfficeHourRegistrationsResult
import com.deelabs.untisplus.api.model.untis.enumeration.OfficeHourRegistrationTimeSlotState
import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InfoCenterOfficeHours(
	uiState: OfficeHoursUiState,
	registrationState: OfficeHourRegistrationUiState,
	selectedOfficeHour: OfficeHour?,
	onSelectOfficeHour: (OfficeHour) -> Unit,
	onDismissOfficeHour: () -> Unit,
) {
	Box(modifier = Modifier.fillMaxSize()) {
		Crossfade(targetState = uiState, label = "InfoCenter Office Hours Content") { state ->
			when (state) {
				OfficeHoursUiState.Loading -> InfoCenterLoading()
				is OfficeHoursUiState.Success -> {
					state.officeHours.fold(
						onSuccess = { officeHours ->
							if (state.isEmpty) {
								Text(
									text = stringResource(R.string.infocenter_officehours_empty),
									textAlign = TextAlign.Center,
									modifier = Modifier.fillMaxWidth()
								)
							} else {
								LazyColumn(
									horizontalAlignment = Alignment.CenterHorizontally,
									modifier = Modifier.fillMaxSize()
								) {
									items(officeHours) { officeHour ->
										OfficeHourItem(officeHour) { onSelectOfficeHour(officeHour) }
									}
								}
							}
						},
						onFailure = { InfoCenterError(it) }
					)
				}
			}
		}

		selectedOfficeHour?.let { officeHour ->
			OfficeHourRegistrationPage(
				officeHour = officeHour,
				registrationState = registrationState,
				onDismiss = onDismissOfficeHour
			)
		}
	}
}

@Composable
private fun OfficeHourItem(item: OfficeHour, onClick: () -> Unit) {
	val body = listOf(
		item.displayNameRooms,
		item.phone,
		item.email
	).filter { it?.isNotEmpty() == true }.joinToString("\n")

	ListItem(
		modifier = Modifier.clickable(onClick = onClick),
		overlineContent = {
			Text(
				formatOfficeHourTime(item.startDateTime.toJavaLocalDateTime(), item.endDateTime.toJavaLocalDateTime())
			)
		},
		headlineContent = { Text(item.displayNameTeacher) },
		supportingContent = if (body.isNotBlank()) {
			{ Text(body) }
		} else null
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficeHourRegistrationPage(
	officeHour: OfficeHour,
	registrationState: OfficeHourRegistrationUiState,
	onDismiss: () -> Unit
) {
	Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
		Column(modifier = Modifier.fillMaxSize()) {
			CenterAlignedTopAppBar(
				title = { Text(stringResource(R.string.parent_office_hours_registration_title)) },
				navigationIcon = {
					IconButton(onClick = onDismiss) {
						Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
					}
				}
			)

			LazyColumn(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier.fillMaxSize()
			) {
				item {
					ListItem(
						headlineContent = { Text(officeHour.displayNameTeacher) },
						supportingContent = {
							Column {
								if (officeHour.displayNameRooms.isNotBlank())
									Text(officeHour.displayNameRooms)
								officeHour.email?.takeIf { it.isNotBlank() }?.let { Text(it) }
								officeHour.phone?.takeIf { it.isNotBlank() }?.let { Text(it) }
							}
						}
					)
				}

				item {
					Text(
						text = stringResource(R.string.parent_office_hours_available_slots),
						style = MaterialTheme.typography.labelLarge,
						modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
					)
				}

				when (registrationState) {
					OfficeHourRegistrationUiState.Loading -> item { InfoCenterLoading() }
					is OfficeHourRegistrationUiState.Success -> {
						registrationState.registrations.fold(
							onSuccess = { registrations ->
								if (registrations.timeSlots.isEmpty()) {
									item {
										Text(
											text = stringResource(R.string.parent_office_hours_no_slots),
											textAlign = TextAlign.Center,
											modifier = Modifier.fillMaxWidth()
										)
									}
								} else {
									items(registrations.timeSlots) { slot ->
										OfficeHourTimeSlotItem(
											start = formatTime(slot.startTime),
											end = formatTime(slot.endTime),
											state = slot.state,
											onRegister = null
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

@Composable
private fun OfficeHourTimeSlotItem(
	start: String,
	end: String,
	state: OfficeHourRegistrationTimeSlotState,
	onRegister: (() -> Unit)?
) {
	ListItem(
		overlineContent = { Text(stringResource(R.string.parent_office_hours_slot_time, start, end)) },
		headlineContent = { Text(stringResource(slotStateLabel(state))) },
		trailingContent = if (state == OfficeHourRegistrationTimeSlotState.FREE && onRegister != null) {
			{
				Button(onClick = onRegister) {
					Text(stringResource(R.string.parent_office_hours_register))
				}
			}
		} else null
	)
}

private fun slotStateLabel(state: OfficeHourRegistrationTimeSlotState) = when (state) {
	OfficeHourRegistrationTimeSlotState.FREE -> R.string.parent_office_hours_slot_free
	OfficeHourRegistrationTimeSlotState.OTHER -> R.string.parent_office_hours_slot_other
	OfficeHourRegistrationTimeSlotState.SELF -> R.string.parent_office_hours_slot_self
}

private fun formatTime(time: LocalTime): String =
	DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time.toJavaLocalTime())

@Composable
private fun formatOfficeHourTime(
	startDateTime: LocalDateTime,
	endDateTime: LocalDateTime,
): String {
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

sealed interface OfficeHoursUiState {
	data object Loading : OfficeHoursUiState

	data class Success(val officeHours: Result<List<OfficeHour>>) : OfficeHoursUiState {
		val isEmpty: Boolean get() = officeHours.getOrDefault(emptyList()).isEmpty()
	}
}

sealed interface OfficeHourRegistrationUiState {
	data object Loading : OfficeHourRegistrationUiState

	data class Success(val registrations: Result<OfficeHourRegistrationsResult>) : OfficeHourRegistrationUiState
}
