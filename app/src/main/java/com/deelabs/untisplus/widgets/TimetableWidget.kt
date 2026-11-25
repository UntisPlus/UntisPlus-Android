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
import androidx.glance.layout.Row
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

class TimetableWidget : GlanceAppWidget() {
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val configuration = WidgetConfigurationStore.read(context, id)
		val timetable = if (configuration == null) {
			WidgetTimetable(message = context.getString(R.string.widget_timetable_configure))
		} else {
			runCatching { WidgetTimetableLoader().load(context, configuration) }
				.getOrDefault(WidgetTimetable(message = context.getString(R.string.widget_timetable_load_error)))
		}

		provideContent { WidgetContent(timetable) }
	}
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

/** Opens the timetable (and configured profile) when the widget is tapped. */
class WidgetOpenActionCallback : ActionCallback {
	override suspend fun onAction(
		context: Context,
		glanceId: GlanceId,
		parameters: ActionParameters,
	) {
		val configuration = WidgetConfigurationStore.read(context, glanceId)
		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
			if (configuration != null) {
				putExtra(MainActivity.EXTRA_LONG_USER_ID, configuration.userId)
				configuration.element?.let { element ->
					putExtra(MainActivity.EXTRA_STRING_ELEMENT_TYPE, element.type.name)
					putExtra(MainActivity.EXTRA_LONG_ELEMENT_ID, element.id)
				}
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

private val lessonStyle = TextStyle(
	color = onSurface,
	fontSize = 14.sp,
	fontWeight = FontWeight.Bold,
)

private val detailStyle = TextStyle(
	color = onSurfaceMuted,
	fontSize = 12.sp,
)

@Composable
private fun WidgetContent(timetable: WidgetTimetable) {
	Column(
		modifier = GlanceModifier
			.fillMaxSize()
			.background(surface)
			.padding(12.dp)
			.clickable(actionRunCallback<WidgetOpenActionCallback>())
	) {
		Text(
			text = timetable.title.ifBlank { LocalContext.current.getString(R.string.widget_timetable) },
			style = titleStyle,
			modifier = GlanceModifier
				.fillMaxWidth()
				.background(headerBackground)
				.padding(horizontal = 8.dp, vertical = 6.dp)
		)

		if (timetable.message != null) {
			Text(
				text = timetable.message,
				style = detailStyle,
				modifier = GlanceModifier.padding(top = 12.dp)
			)
		} else {
			timetable.lessons.forEach { lesson ->
				Row(
					modifier = GlanceModifier
						.fillMaxWidth()
						.padding(vertical = 4.dp)
				) {
					Text(text = lesson.time, style = detailStyle)
					Column(modifier = GlanceModifier.padding(start = 10.dp)) {
						Text(text = lesson.subject, style = lessonStyle)
						if (lesson.detail.isNotBlank()) {
							Text(text = lesson.detail, style = detailStyle)
						}
					}
				}
			}
		}
	}
}
