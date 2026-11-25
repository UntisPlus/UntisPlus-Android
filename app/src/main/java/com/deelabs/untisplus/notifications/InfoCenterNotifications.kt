package com.deelabs.untisplus.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deelabs.untisplus.R
import com.deelabs.untisplus.workers.TimetableChangeNotifications

/** FOSS-only local notifications for Info Center refresh results. */
object InfoCenterNotifications {
	const val CHANNEL_ID = "notifications_channel_infocenter"
	const val NOTIFICATION_ID = 5403

	fun ensureChannels(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		manager.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.notifications_channel_infocenter),
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = context.getString(R.string.notifications_channel_infocenter_desc)
			}
		)
	}

	fun canPost(context: Context): Boolean =
		TimetableChangeNotifications.canPostNotifications(context)

	/** Posts one summary notification for all new entries; repeated posts replace it. */
	fun post(context: Context, entries: List<InfoCenterNotificationEntry>): Boolean {
		if (entries.isEmpty() || !canPost(context)) return false

		return try {
			ensureChannels(context)
			NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, entries))
			true
		} catch (e: SecurityException) {
			Log.w("InfoCenterNotifs", "Notification permission missing", e)
			false
		}
	}

	fun cancel(context: Context) {
		NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
	}

	private fun build(context: Context, entries: List<InfoCenterNotificationEntry>) =
		NotificationCompat.Builder(context, CHANNEL_ID)
			.setContentTitle(context.getString(R.string.notifications_infocenter_title))
			.setContentText(context.getString(R.string.notifications_infocenter_text, entries.size))
			.setStyle(NotificationCompat.BigTextStyle().bigText(entries.summary(context)))
			.setSmallIcon(R.drawable.infocenter_messages)
			.setContentIntent(TimetableChangeNotifications.contentIntent(context))
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_EMAIL)
			.build()

	private fun List<InfoCenterNotificationEntry>.summary(context: Context): String =
		joinToString("\n") { entry ->
			val title = entry.title.ifBlank {
				context.getString(R.string.notifications_infocenter_entry_fallback)
			}
			if (entry.text.isBlank()) title else "$title: ${entry.text}"
		}
}

/** Small seam so the refresh controller can be unit tested without Android. */
interface InfoCenterNotifier {
	fun canPost(): Boolean
	fun post(entries: List<InfoCenterNotificationEntry>): Boolean
	fun cancel()
}

class AndroidInfoCenterNotifier(
	private val context: Context,
) : InfoCenterNotifier {
	override fun canPost(): Boolean = InfoCenterNotifications.canPost(context)

	override fun post(entries: List<InfoCenterNotificationEntry>): Boolean =
		InfoCenterNotifications.post(context, entries)

	override fun cancel() = InfoCenterNotifications.cancel(context)
}
