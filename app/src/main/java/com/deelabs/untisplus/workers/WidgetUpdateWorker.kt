package com.deelabs.untisplus.workers

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.deelabs.untisplus.widgets.MessagesWidget
import com.deelabs.untisplus.widgets.TimetableWidget

/**
 * Refreshes all configured timetable and daily-message widgets. Each widget
 * loads its own persisted profile, so this worker only asks Glance to re-render.
 */
class WidgetUpdateWorker constructor(
	context: Context,
	params: WorkerParameters,
) : CoroutineWorker(context, params) {
	companion object {
		private const val LOG_TAG = "WidgetUpdate"

		fun enqueue(workManager: WorkManager) {
			workManager.enqueue(
				OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
					.addTag("WidgetUpdateWork")
					.build()
			)
		}
	}

	override suspend fun doWork(): Result = try {
		val manager = GlanceAppWidgetManager(applicationContext)
		val timetableWidget = TimetableWidget()
		manager.getGlanceIds(TimetableWidget::class.java)
			.forEach { glanceId -> timetableWidget.update(applicationContext, glanceId) }
		val messagesWidget = MessagesWidget()
		manager.getGlanceIds(MessagesWidget::class.java)
			.forEach { glanceId -> messagesWidget.update(applicationContext, glanceId) }
		Result.success()
	} catch (e: Exception) {
		Log.w(LOG_TAG, "Widget refresh failed", e)
		Result.retry()
	}
}
