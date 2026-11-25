package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.model.proxy.ProxyMe
import kotlinx.coroutines.flow.StateFlow

/**
 * Persisted result of the one-time UntisPlus compatibility probe.
 *
 * [checked] is false until `/me` has been attempted for that user. A checked
 * profile with [supported] false means the configured host is not a compatible
 * UntisPlus proxy (old custom proxy, official server, or a failed probe).
 */
data class ProxyCompatibilitySnapshot(
	val checked: Boolean = false,
	val supported: Boolean = false,
	val me: ProxyMe? = null,
)

/** Platform storage for the active user's compatibility snapshot. */
interface ProxyCompatibilityStore {
	/** Snapshot for the active user. Empty until the login-time probe has run. */
	val snapshot: StateFlow<ProxyCompatibilitySnapshot>

	suspend fun save(userId: Long, snapshot: ProxyCompatibilitySnapshot)
}
