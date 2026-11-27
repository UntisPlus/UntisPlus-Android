package com.deelabs.untisplus.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.database.entities.getDisplayedName
import com.deelabs.untisplus.helpers.AppTheme
import com.deelabs.untisplus.widgets.MessagesWidget
import com.deelabs.untisplus.widgets.WidgetConfigurationStore
import com.deelabs.untisplus.workers.WidgetUpdateWorker
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Configures a daily-messages widget with one of the locally saved profiles. */
class MessagesWidgetConfigureActivity : ComponentActivity(), KoinComponent {
	companion object {
		private const val LOG_TAG = "MessagesWidgetConfigure"
	}

	private val userDao: UserDao by inject()

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
				var users by remember { mutableStateOf(emptyList<User>()) }
				var loaded by remember { mutableStateOf(false) }

				LaunchedEffect(Unit) {
					users = userDao.getAllAsync()
					loaded = true
				}

				if (loaded && users.isEmpty()) {
					Text(
						text = stringResource(R.string.widget_select_profile),
						modifier = Modifier.padding(16.dp)
					)
				} else {
					ProfileList(users = users) { userId -> saveConfiguration(appWidgetId, userId) }
				}
			}
		}
	}

	private fun saveConfiguration(appWidgetId: Int, userId: Long) {
		// The launcher removes a widget when configuration finishes with RESULT_CANCELED,
		// so acknowledge it before the asynchronous Glance state write.
		setResult(
			Activity.RESULT_OK,
			Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
		)

		lifecycleScope.launch {
			try {
				val glanceId = GlanceAppWidgetManager(this@MessagesWidgetConfigureActivity)
					.getGlanceIdBy(appWidgetId)
				WidgetConfigurationStore.write(this@MessagesWidgetConfigureActivity, glanceId, userId, null)
				MessagesWidget().update(this@MessagesWidgetConfigureActivity, glanceId)
				WidgetUpdateWorker.enqueue(WorkManager.getInstance(this@MessagesWidgetConfigureActivity))
			} catch (t: Throwable) {
				Log.e(LOG_TAG, "Failed to finish messages widget configuration", t)
				Toast.makeText(
					this@MessagesWidgetConfigureActivity,
					R.string.widget_configuration_failed,
					Toast.LENGTH_LONG
				).show()
			} finally {
				finish()
			}
		}
	}
}

@Composable
private fun ProfileList(users: List<User>, onSelect: (Long) -> Unit) {
	LazyColumn(modifier = Modifier.fillMaxSize()) {
		items(users, key = { it.id }) { user ->
			TextButton(
				onClick = { onSelect(user.id) },
				modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
			) {
				Text(text = user.getDisplayedName())
			}
		}
	}
}
