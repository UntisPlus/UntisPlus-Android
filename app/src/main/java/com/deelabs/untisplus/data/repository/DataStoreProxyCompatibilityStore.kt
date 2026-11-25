package com.deelabs.untisplus.data.repository

import androidx.datastore.core.DataStore
import com.deelabs.untisplus.api.model.proxy.ProxyMe
import com.deelabs.untisplus.data.settings.model.Settings
import com.deelabs.untisplus.data.settings.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores the login-time proxy compatibility result in the existing per-user
 * proto settings, so no Room migration is needed.
 */
class DataStoreProxyCompatibilityStore constructor(
	private val userRepository: UserRepository,
	private val dataStore: DataStore<Settings>,
) : ProxyCompatibilityStore {
	private val json = Json { ignoreUnknownKeys = true }
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override val snapshot: StateFlow<ProxyCompatibilitySnapshot> =
		combine(userRepository.userState, dataStore.data) { userState, settings ->
			val userId = (userState as? UserRepository.UserState.User)?.user?.id
			if (userId == null) {
				ProxyCompatibilitySnapshot()
			} else {
				settings.userSettingsMap
					.getOrDefault(userId, UserSettings.getDefaultInstance())
					.toSnapshot()
			}
		}.stateIn(scope, SharingStarted.Eagerly, ProxyCompatibilitySnapshot())

	override suspend fun save(userId: Long, snapshot: ProxyCompatibilitySnapshot) {
		dataStore.updateData { settings ->
			val userSettings = settings.userSettingsMap
				.getOrDefault(userId, UserSettings.getDefaultInstance())
			val meJson = snapshot.me?.let { json.encodeToString<ProxyMe>(it) }.orEmpty()

			settings.toBuilder()
				.putUserSettings(
					userId,
					userSettings.toBuilder()
						.setProxyCompatibilityChecked(snapshot.checked)
						.setProxyCompatibilitySupported(snapshot.supported)
						.setProxyMeJson(meJson)
						.build()
				)
				.build()
		}
	}

	private fun UserSettings.toSnapshot(): ProxyCompatibilitySnapshot {
		val me = proxyMeJson.takeIf { it.isNotEmpty() }
			?.let { runCatching { json.decodeFromString<ProxyMe>(it) }.getOrNull() }

		return ProxyCompatibilitySnapshot(
			checked = proxyCompatibilityChecked,
			supported = proxyCompatibilitySupported,
			me = me,
		)
	}
}
