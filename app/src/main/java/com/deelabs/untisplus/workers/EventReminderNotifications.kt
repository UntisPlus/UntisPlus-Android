package com.deelabs.untisplus.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deelabs.untisplus.R

/** Notification channel and posting helpers for local homework/exam reminders. */
internal object EventReminderNotifications {
	const val CHANNEL_ID = "notifications_channel_eventreminders"

	fun ensureChannels(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.notifications_channel_eventreminders),
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = context.getString(R.string.notifications_channel_eventreminders_desc)
			}
		)
	}

	fun notification(context: Context, title: String, text: String): Notification =
		NotificationCompat.Builder(context, CHANNEL_ID)
			.setContentTitle(title)
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setSmallIcon(R.drawable.infocenter_events)
			.setContentIntent(TimetableChangeNotifications.contentIntent(context))
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
			.build()

	fun post(context: Context, notificationId: Int, title: String, text: String) {
		if (!TimetableChangeNotifications.canPostNotifications(context)) return
		try {
			NotificationManagerCompat.from(context).notify(notificationId, notification(context, title, text))
		} catch (e: SecurityException) {
			Log.w("EventReminderNotifs", "Notification permission missing", e)
		}
	}

	fun cancel(context: Context, notificationIds: Collection<Int>) {
		if (notificationIds.isEmpty()) return
		NotificationManagerCompat.from(context).apply {
			notificationIds.forEach(::cancel)
		}
	}
}
