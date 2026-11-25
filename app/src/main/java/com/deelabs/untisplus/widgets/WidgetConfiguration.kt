package com.deelabs.untisplus.widgets

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement

/**
 * Persisted configuration of a timetable widget. A null element means the
 * configured profile's personal timetable.
 */
internal data class WidgetConfiguration(
	val userId: Long,
	val element: PeriodElement?,
)

internal object WidgetConfigurationStore {
	private const val KEY_USER_ID = "userId"
	private const val KEY_ELEMENT_ID = "elementId"
	private const val KEY_ELEMENT_TYPE = "elementType"

	private val userIdKey = longPreferencesKey(KEY_USER_ID)
	private val elementIdKey = longPreferencesKey(KEY_ELEMENT_ID)
	private val elementTypeKey = stringPreferencesKey(KEY_ELEMENT_TYPE)

	suspend fun read(context: Context, glanceId: GlanceId): WidgetConfiguration? {
		val preferences: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
		val userId = preferences[userIdKey] ?: return null
		val type = preferences[elementTypeKey]?.let { runCatching { ElementType.valueOf(it) }.getOrNull() }
		val elementId = preferences[elementIdKey]
		return WidgetConfiguration(
			userId = userId,
			element = if (type != null && elementId != null) PeriodElement(type, elementId) else null,
		)
	}

	suspend fun write(
		context: Context,
		glanceId: GlanceId,
		userId: Long,
		element: PeriodElement?,
	) {
		updateAppWidgetState(context, glanceId) { preferences ->
			preferences[userIdKey] = userId
			if (element == null) {
				preferences.remove(elementIdKey)
				preferences.remove(elementTypeKey)
			} else {
				preferences[elementIdKey] = element.id
				preferences[elementTypeKey] = element.type.name
			}
		}
	}
}
