package com.deelabs.untisplus.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import com.sapuseven.compose.protostore.data.MultiUserSettingsRepository
import com.deelabs.untisplus.data.settings.model.Settings
import com.deelabs.untisplus.data.settings.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserSettingsRepository constructor(
	private val userRepository: UserRepository,
	private val dataStore: DataStore<Settings>
) : MultiUserSettingsRepository<Settings, Settings.Builder, UserSettings, UserSettings.Builder>(
	dataStore
) {
	fun getSettings(userId: Long): Flow<UserSettings> {
		return getAllSettings().map { userSettings -> userSettings.userSettingsMap.getOrDefault(userId, UserSettings.getDefaultInstance()) }
	}

	/** Updates one profile's settings, even when it is not the active profile. */
	suspend fun updateSettingsForUser(userId: Long, transform: UserSettings.Builder.() -> Unit) {
		dataStore.updateData { currentData ->
			val userSettings = getUserSettings(currentData, userId)
			currentData.toBuilder()
				.putUserSettings(userId, userSettings.toBuilder().apply(transform).build())
				.build()
		}
	}

	private fun getUserSettings(dataStore: Settings, userId: Long?): UserSettings {
		Log.d("SettingsRepository", "DataStore getUserSettings #$userId")

		return dataStore.userSettingsMap.getOrDefault(userId, UserSettings.getDefaultInstance())
	}

	override fun getUserSettings(dataStore: Settings): UserSettings {
		return getUserSettings(dataStore, userRepository.currentUser?.id)
	}

	override fun updateUserSettings(currentData: Settings, userSettings: UserSettings): Settings {
		return currentData.toBuilder()
			.apply {
				putUserSettings(userRepository.currentUser!!.id, userSettings)
			}
			.build()
	}
}

fun <T> T.withDefault(isPresent: Boolean, defaultValue: T): T = if (isPresent) this else defaultValue
