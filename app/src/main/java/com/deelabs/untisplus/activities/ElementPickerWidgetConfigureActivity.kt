package com.deelabs.untisplus.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.helpers.AppTheme
import com.deelabs.untisplus.ui.dialogs.ElementPickerDialogFullscreen
import com.deelabs.untisplus.widgets.TimetableWidget
import com.deelabs.untisplus.widgets.WidgetConfigurationStore
import com.deelabs.untisplus.workers.WidgetUpdateWorker
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Configures a timetable widget with the active profile and a timetable element. */
class ElementPickerWidgetConfigureActivity : ComponentActivity(), KoinComponent {
	companion object {
		private const val LOG_TAG = "WidgetConfigure"
	}

	private val userDao: UserDao by inject()
	private val userRepository: UserRepository by inject()
	private val masterDataRepository: MasterDataRepository by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setResult(RESULT_CANCELED)

		val appWidgetId = intent?.extras?.getInt(
			AppWidgetManager.EXTRA_APPWIDGET_ID,
			AppWidgetManager.INVALID_APPWIDGET_ID
		) ?: AppWidgetManager.INVALID_APPWIDGET_ID

		if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish()
			return
		}

		setContent {
			AppTheme {
				var userId by remember { mutableLongStateOf(userRepository.currentUser?.id ?: -1L) }

				LaunchedEffect(Unit) {
					if (userId == -1L) {
						userId = (userRepository.userState.first() as? UserRepository.UserState.User)?.user?.id
							?: userDao.getAllAsync().firstOrNull()?.id
							?: -1L
					}
				}

				if (userId == -1L) {
					Text(stringResource(R.string.widget_select_profile))
				} else {
					ElementPickerDialogFullscreen(
						title = { Text(stringResource(R.string.widget_configuration)) },
						masterDataRepository = masterDataRepository,
						onDismiss = { success -> if (!success) finish() },
						onSelect = { element -> saveConfiguration(appWidgetId, userId, element) }
					)
				}
			}
		}
	}

	private fun saveConfiguration(appWidgetId: Int, userId: Long, element: PeriodElement?) {
		// The launcher removes a widget when its configuration activity finishes
		// with RESULT_CANCELED, so acknowledge the widget before doing suspend work.
		setResult(
			Activity.RESULT_OK,
			Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
		)

		lifecycleScope.launch {
			try {
				val glanceId = GlanceAppWidgetManager(this@ElementPickerWidgetConfigureActivity)
					.getGlanceIdBy(appWidgetId)
				WidgetConfigurationStore.write(this@ElementPickerWidgetConfigureActivity, glanceId, userId, element)
				TimetableWidget().update(this@ElementPickerWidgetConfigureActivity, glanceId)
				WidgetUpdateWorker.enqueue(WorkManager.getInstance(this@ElementPickerWidgetConfigureActivity))
			} catch (t: Throwable) {
				Log.e(LOG_TAG, "Failed to finish widget configuration", t)
				Toast.makeText(
					this@ElementPickerWidgetConfigureActivity,
					R.string.widget_configuration_failed,
					Toast.LENGTH_LONG
				).show()
			} finally {
				finish()
			}
		}
	}
}
