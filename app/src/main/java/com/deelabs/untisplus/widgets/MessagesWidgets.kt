package com.deelabs.untisplus.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.deelabs.untisplus.R
import com.deelabs.untisplus.activities.MainActivity

private val surface = ColorProvider(R.color.widget_surface)
private val onSurface = ColorProvider(R.color.widget_on_surface)
private val onSurfaceMuted = ColorProvider(R.color.widget_on_surface_muted)
private val headerBackground = ColorProvider(R.color.widget_header)
private val onHeader = ColorProvider(R.color.widget_on_header)

class MessagesWidget : GlanceAppWidget() {
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val configuration = WidgetConfigurationStore.read(context, id)
		val dailyMessages = if (configuration == null) {
			WidgetDailyMessages(message = context.getString(R.string.widget_timetable_configure))
		} else {
			runCatching { WidgetDailyMessagesLoader().load(context, configuration) }
				.getOrDefault(WidgetDailyMessages(message = context.getString(R.string.widget_messages_load_error)))
		}

		provideContent { WidgetContent(dailyMessages) }
	}
}

class MessagesWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = MessagesWidget()
}

/** Opens the configured profile's Info Center when the widget is tapped. */
class WidgetOpenMessagesActionCallback : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters,
	) {
		val configuration = WidgetConfigurationStore.read(context, glanceId)
		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
			putExtra(MainActivity.EXTRA_BOOLEAN_WIDGET_MESSAGES, true)
			if (configuration != null) {
				putExtra(MainActivity.EXTRA_LONG_USER_ID, configuration.userId)
			}
		}
		context.startActivity(intent)
	}
}

private val titleStyle = TextStyle(
	color = onHeader,
	fontSize = 15.sp,
	fontWeight = FontWeight.Bold,
)

private val subjectStyle = TextStyle(
	color = onSurface,
	fontSize = 14.sp,
	fontWeight = FontWeight.Bold,
)

private val detailStyle = TextStyle(
	color = onSurfaceMuted,
	fontSize = 12.sp,
)

@Composable
private fun WidgetContent(dailyMessages: WidgetDailyMessages) {
	val context = LocalContext.current
	Column(
		modifier = GlanceModifier
			.fillMaxSize()
			.background(surface)
			.padding(12.dp)
			.clickable(actionRunCallback<WidgetOpenMessagesActionCallback>())
	) {
		Text(
			text = context.getString(R.string.widget_daily_messages),
			style = titleStyle,
			modifier = GlanceModifier
				.fillMaxWidth()
				.background(headerBackground)
				.padding(horizontal = 8.dp, vertical = 6.dp)
		)

		if (dailyMessages.header.isNotBlank()) {
			Text(
				text = dailyMessages.header,
				style = detailStyle,
				modifier = GlanceModifier.padding(top = 6.dp)
			)
		}

		if (dailyMessages.message != null) {
			Text(
				text = dailyMessages.message,
				style = detailStyle,
				modifier = GlanceModifier.padding(top = 10.dp)
			)
		} else {
			dailyMessages.messages.forEach { message ->
				Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
					Text(text = message.subject, style = subjectStyle)
					if (message.body.isNotBlank()) {
						Text(text = message.body, style = detailStyle)
					}
				}
			}
			if (dailyMessages.hiddenCount > 0) {
				Text(
					text = context.getString(R.string.widget_messages_more, dailyMessages.hiddenCount),
					style = detailStyle,
					modifier = GlanceModifier.padding(top = 8.dp)
				)
			}
		}
	}
}
