package com.deelabs.untisplus.ui.pages.settings.fragments

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sapuseven.compose.protostore.ui.preferences.ListPreference
import com.sapuseven.compose.protostore.ui.preferences.NumericInputPreference
import com.sapuseven.compose.protostore.ui.preferences.Preference
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.sapuseven.compose.protostore.ui.preferences.SwitchPreference
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.ProxyStreamTargetType
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.data.settings.model.NotificationVisibility
import com.deelabs.untisplus.services.TimetableChangeService
import com.deelabs.untisplus.ui.common.disabled
import com.deelabs.untisplus.ui.dialogs.ElementPickerDialogFullscreen
import com.deelabs.untisplus.notifications.InfoCenterNotifications
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.settings.SettingsScreenViewModel
import com.deelabs.untisplus.workers.EventReminderSyncWorker
import com.deelabs.untisplus.workers.NotificationSetupWorker
import com.deelabs.untisplus.workers.TimetableChangeWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsCategoryNotifications(navController: NavController, viewModel: SettingsScreenViewModel) {
	val scope = rememberCoroutineScope()
	val context = LocalContext.current

	var notificationsMessageVisible by rememberSaveable { mutableStateOf(false) }

	var notificationPermissionGranted by remember {
		mutableStateOf(
			Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
				ContextCompat.checkSelfPermission(
					context,
					android.Manifest.permission.POST_NOTIFICATIONS
				) == PackageManager.PERMISSION_GRANTED
		)
	}

	val notificationPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		notificationPermissionGranted = granted
		notificationsMessageVisible = !granted

		scope.launch {
			viewModel.userSettingsRepository.updateSettings {
				notificationsEnable = granted
			}
		}
	}

	fun enqueueNotificationSetup() = scope.launch {
		delay(1000) // Delay to ensure the settings are updated before worker is enqueued
		WorkManager.getInstance(context).apply {
			enqueue(OneTimeWorkRequestBuilder<NotificationSetupWorker>().build())
		}
	}

	fun refreshEventReminders() = scope.launch {
		delay(1000) // Let protostore persist the opt-in before the worker reads it.
		EventReminderSyncWorker.schedule(context)
		EventReminderSyncWorker.syncNow(context)
	}

	fun restartTimetableStream() = scope.launch {
		// The service reads the target when the SSE connection opens, so a
		// changed target only takes effect after reconnecting.
		TimetableChangeService.stop(context)
		delay(1000) // Let protostore persist the new value first.
		if (viewModel.userSettingsRepository.getSettings().first().timetableChangeNotifications) {
			TimetableChangeService.start(context)
		}
	}

	fun clearNotifications() {
		(context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll()
	}

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val visible by remember {
			viewModel.userSettingsRepository.getSettings().map { it.notificationsEnable }
		}.collectAsState(initial = false)
		ScheduleExactAlarmInfoMessage(
			visible = visible,
			primaryText = R.string.preference_notifications_exact_alarms_unavailable,
			secondaryText = stringResource(R.string.preference_notifications_exact_alarms_unavailable_desc, stringResource(R.string.app_name))
		)
	}

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		val notificationsEnabled by remember {
			viewModel.userSettingsRepository.getSettings().map { it.notificationsEnable }
		}.collectAsState(initial = false)
		NotificationsInfoMessage(visible = !notificationsEnabled && notificationsMessageVisible)
	}

	PreferenceGroup(
		title = stringResource(R.string.preference_category_notifications_break)
	) {
		LaunchedEffect(Unit) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
				viewModel.userSettingsRepository.updateSettings {
					notificationsEnable = false
				}
			}
		}

		SwitchPreference(
			title = { Text(stringResource(R.string.preference_notifications_enable)) },
			summary = { Text(stringResource(R.string.preference_notifications_enable_desc)) },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsEnable },
			onValueChange = {
				notificationsEnable = if (it) {
					if (notificationPermissionGranted) {
						notificationsMessageVisible = false
						enqueueNotificationSetup()
						if (this.eventRemindersEnabled) refreshEventReminders() else EventReminderSyncWorker.schedule(context)
						true
					} else {
						notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
						false
					}
				} else {
					clearNotifications()
					EventReminderSyncWorker.cancel(context, viewModel.currentUserId())
					TimetableChangeWorker.cancel(context)
					TimetableChangeService.stop(context)
					scope.launch {
						viewModel.userSettingsRepository.updateSettings {
							clearInfocenterMessageNotificationsSeen()
							infocenterMessageNotificationsInitialized = false
						}
					}
					false
				}
			}
		)

		SwitchPreference(
			title = { Text(stringResource(R.string.preference_notifications_multiple)) },
			summary = { Text(stringResource(R.string.preference_notifications_multiple_desc)) },
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsInMultiple },
			onValueChange = {
				notificationsInMultiple = it
				enqueueNotificationSetup()
			}
		)

		SwitchPreference(
			title = { Text(stringResource(R.string.preference_notifications_first_lesson)) },
			summary = { Text(stringResource(R.string.preference_notifications_first_lesson_desc)) },
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsBeforeFirst },
			onValueChange = {
				notificationsBeforeFirst = it
				enqueueNotificationSetup()
			}
		)

		NumericInputPreference(
			title = { Text(stringResource(R.string.preference_notifications_first_lesson_time)) },
			unit = stringResource(R.string.preference_notifications_first_lesson_time_unit),
			enabledCondition = { it.notificationsEnable && it.notificationsBeforeFirst },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsBeforeFirstTime },
			onValueChange = {
				notificationsBeforeFirstTime = it
				enqueueNotificationSetup()
			}
		)
	}

	PreferenceGroup(stringResource(R.string.preference_category_notifications_infocenter)) {
		SwitchPreference(
			title = { Text(stringResource(R.string.preference_notifications_infocenter)) },
			summary = { Text(stringResource(R.string.preference_notifications_infocenter_desc)) },
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.infocenterMessageNotifications },
			onValueChange = { enabled ->
				scope.launch {
					viewModel.userSettingsRepository.updateSettings {
						infocenterMessageNotifications = enabled
						if (!enabled) {
							clearInfocenterMessageNotificationsSeen()
							infocenterMessageNotificationsInitialized = false
						}
					}
					if (enabled) InfoCenterNotifications.ensureChannels(context)
					else InfoCenterNotifications.cancel(context)
				}
			}
		)
	}

	PreferenceGroup(stringResource(R.string.preference_category_notifications_events)) {
		SwitchPreference(
			title = { Text(stringResource(R.string.preference_notifications_event_reminders)) },
			summary = { Text(stringResource(R.string.preference_notifications_event_reminders_desc)) },
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.eventRemindersEnabled },
			onValueChange = {
				eventRemindersEnabled = it
				if (it) refreshEventReminders() else EventReminderSyncWorker.cancel(context, viewModel.currentUserId())
			}
		)
	}

	if (viewModel.proxyAvailable) {
		val streamTargetSetting by remember {
			viewModel.userSettingsRepository.getSettings().map {
				it.timetableChangeStreamTargetType to it.timetableChangeStreamTargetId
			}
		}.collectAsState(initial = ProxyStreamTargetType.PERSONAL.storedName to 0L)
		val streamTargetType = ProxyStreamTargetType.fromStored(streamTargetSetting.first)
		val streamElementId = when (streamTargetType) {
			ProxyStreamTargetType.PERSON -> viewModel.proxyPersonId
			else -> streamTargetSetting.second.takeIf { it > 0 }
		}
		var showStreamElementPicker by remember { mutableStateOf(false) }

		val streamElementLabel = when {
			!streamTargetType.needsId -> stringResource(R.string.preference_notifications_stream_element_none)
			streamTargetType == ProxyStreamTargetType.PERSON -> stringResource(
				R.string.preference_notifications_stream_person_self,
				streamElementId ?: 0L
			)
			streamElementId != null -> viewModel.masterDataRepository.getShortName(
				streamElementId,
				streamTargetType.pickerElementType(),
				stringResource(R.string.preference_notifications_stream_element_none)
			)
			else -> stringResource(R.string.preference_notifications_stream_element_none)
		}

		PreferenceGroup(stringResource(R.string.preference_category_notifications_timetable_changes)) {
			SwitchPreference(
				title = { Text(stringResource(R.string.preference_notifications_timetable_changes)) },
				summary = { Text(stringResource(R.string.preference_notifications_timetable_changes_desc)) },
				enabledCondition = { it.notificationsEnable },
				settingsRepository = viewModel.userSettingsRepository,
				value = { it.timetableChangeNotifications },
				onValueChange = {
					timetableChangeNotifications = it
					if (it) {
						TimetableChangeWorker.schedule(context)
						TimetableChangeService.start(context)
					} else {
						TimetableChangeWorker.cancel(context)
						TimetableChangeService.stop(context)
					}
				}
			)

			SwitchPreference(
				title = { Text(stringResource(R.string.preference_notifications_timetable_changes_wifi_only)) },
				summary = { Text(stringResource(R.string.preference_notifications_timetable_changes_wifi_only_desc)) },
				enabledCondition = { it.notificationsEnable && it.timetableChangeNotifications },
				settingsRepository = viewModel.userSettingsRepository,
				value = { it.timetableChangeWifiOnly },
				onValueChange = {
					timetableChangeWifiOnly = it
					// Re-arm the watcher so the new network policy applies now.
					restartTimetableStream()
				}
			)

			ListPreference(
				title = { Text(stringResource(R.string.preference_notifications_stream_target)) },
				supportingContent = { value, enabled ->
					Text(value.second, modifier = Modifier.disabled(!enabled))
				},
				entries = stringArrayResource(id = R.array.preference_notifications_stream_target_values),
				entryLabels = stringArrayResource(id = R.array.preference_notifications_stream_target),
				enabledCondition = { it.notificationsEnable && it.timetableChangeNotifications },
				settingsRepository = viewModel.userSettingsRepository,
				value = { it.timetableChangeStreamTargetType },
				onValueChange = { targetType ->
					timetableChangeStreamTargetType = targetType
					if (targetType == ProxyStreamTargetType.PERSON.storedName) {
						timetableChangeStreamTargetId = viewModel.proxyPersonId ?: 0L
					}
					restartTimetableStream()
				}
			)

			Preference(
				title = { Text(stringResource(R.string.preference_notifications_stream_element)) },
				summary = {
					Text(
						if (streamTargetType.needsId) streamElementLabel
						else stringResource(R.string.preference_notifications_stream_element_none)
					)
				},
				settingsRepository = viewModel.userSettingsRepository,
				value = { it.timetableChangeStreamTargetId },
				scope = scope,
				enabledCondition = {
					it.notificationsEnable && it.timetableChangeNotifications &&
						ProxyStreamTargetType.fromStored(it.timetableChangeStreamTargetType).needsId
				},
				onClick = {
					if (streamTargetType == ProxyStreamTargetType.PERSON) {
						scope.launch {
							viewModel.userSettingsRepository.updateSettings {
								timetableChangeStreamTargetId = viewModel.proxyPersonId ?: 0L
							}
							restartTimetableStream()
						}
					} else {
						showStreamElementPicker = true
					}
				}
			)

			if (showStreamElementPicker) {
				ElementPickerDialogFullscreen(
					title = { Text(stringResource(R.string.preference_notifications_stream_element)) },
					masterDataRepository = viewModel.masterDataRepository,
					initialType = streamTargetType.pickerElementType(),
					hideTypeSelection = true,
					onDismiss = { showStreamElementPicker = false },
					onSelect = { element ->
						showStreamElementPicker = false
						if (element != null) {
							scope.launch {
								viewModel.userSettingsRepository.updateSettings {
									timetableChangeStreamTargetType = streamTargetType.storedName
									timetableChangeStreamTargetId = element.id
								}
								restartTimetableStream()
							}
						}
					}
				)
			}

			Preference(
				title = { Text(stringResource(R.string.preference_notifications_subscriptions)) },
				summary = { Text(stringResource(R.string.preference_notifications_subscriptions_desc)) },
				onClick = { navController.navigate(AppRoutes.Settings.ChangeSubscriptions) },
				leadingContent = {
					Icon(
						painter = painterResource(id = R.drawable.settings_notifications),
						contentDescription = null
					)
				}
			)
		}
	}

	PreferenceGroup(stringResource(id = R.string.preference_category_notifications_visible_fields)) {
		ListPreference(
			title = { Text(stringResource(R.string.all_subjects)) },
			supportingContent = { value, enabled ->
				Text(
					value.second,
					modifier = Modifier.disabled(!enabled)
				)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.all_subject),
					contentDescription = null
				)
			},
			entries = stringArrayResource(id = R.array.preference_notifications_visibility_values),
			entryLabels = stringArrayResource(id = R.array.preference_notifications_visibility),
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsVisibilitySubjects.number.toString() },
			onValueChange = { notificationsVisibilitySubjects = NotificationVisibility.forNumber(it.toInt()) }
		)

		ListPreference(
			title = { Text(stringResource(R.string.all_rooms)) },
			supportingContent = { value, enabled ->
				Text(
					value.second,
					modifier = Modifier.disabled(!enabled)
				)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.all_rooms),
					contentDescription = null
				)
			},
			entries = stringArrayResource(id = R.array.preference_notifications_visibility_values),
			entryLabels = stringArrayResource(id = R.array.preference_notifications_visibility),
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsVisibilityRooms.number.toString() },
			onValueChange = { notificationsVisibilityRooms = NotificationVisibility.forNumber(it.toInt()) }
		)

		ListPreference(
			title = { Text(stringResource(R.string.all_teachers)) },
			supportingContent = { value, enabled ->
				Text(
					value.second,
					modifier = Modifier.disabled(!enabled)
				)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.all_teachers),
					contentDescription = null
				)
			},
			entries = stringArrayResource(id = R.array.preference_notifications_visibility_values),
			entryLabels = stringArrayResource(id = R.array.preference_notifications_visibility),
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsVisibilityTeachers.number.toString() },
			onValueChange = { notificationsVisibilityTeachers = NotificationVisibility.forNumber(it.toInt()) }
		)

		ListPreference(
			title = { Text(stringResource(R.string.all_classes)) },
			supportingContent = { value, enabled ->
				Text(
					value.second,
					modifier = Modifier.disabled(!enabled)
				)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.all_classes),
					contentDescription = null
				)
			},
			entries = stringArrayResource(id = R.array.preference_notifications_visibility_values),
			entryLabels = stringArrayResource(id = R.array.preference_notifications_visibility),
			enabledCondition = { it.notificationsEnable },
			settingsRepository = viewModel.userSettingsRepository,
			value = { it.notificationsVisibilityClasses.number.toString() },
			onValueChange = { notificationsVisibilityClasses = NotificationVisibility.forNumber(it.toInt()) }
		)
	}
	Preference(
		title = { Text(stringResource(R.string.preference_notifications_clear)) },
		onClick = {
			clearNotifications()
		},
		leadingContent = {
			Icon(
				painter = painterResource(R.drawable.settings_notifications_clear_all),
				contentDescription = null
			)
		}
	)
}

private fun ProxyStreamTargetType.pickerElementType(): ElementType? = when (this) {
	ProxyStreamTargetType.CLASS -> ElementType.CLASS
	ProxyStreamTargetType.TEACHER -> ElementType.TEACHER
	ProxyStreamTargetType.ROOM -> ElementType.ROOM
	ProxyStreamTargetType.SUBJECT -> ElementType.SUBJECT
	else -> null
}
