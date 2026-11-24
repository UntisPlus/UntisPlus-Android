package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.client.ProxyAbsenceApi
import com.deelabs.untisplus.api.client.ProxyAnalyticsApi
import com.deelabs.untisplus.api.client.ProxyCalendarApi
import com.deelabs.untisplus.api.client.ProxyHomeworkApi
import com.deelabs.untisplus.api.client.ProxyStatusApi
import com.deelabs.untisplus.api.client.ProxyStreamEvent
import com.deelabs.untisplus.api.client.ProxySubscriptionsApi
import com.deelabs.untisplus.api.client.ProxyTimetableApi
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.model.proxy.AnalyticsReportRequest
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarOptions
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarToken
import com.deelabs.untisplus.api.model.proxy.ProxyCalendarTokenRequest
import com.deelabs.untisplus.api.model.proxy.ProxyCreated
import com.deelabs.untisplus.api.model.proxy.ProxyDeleted
import com.deelabs.untisplus.api.model.proxy.ProxyHealth
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlag
import com.deelabs.untisplus.api.model.proxy.ProxyHomeworkFlags
import com.deelabs.untisplus.api.model.proxy.ProxyMe
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyRequest
import com.deelabs.untisplus.api.model.proxy.ProxyNtfyTopics
import com.deelabs.untisplus.api.model.proxy.ProxyPermissions
import com.deelabs.untisplus.api.model.proxy.ProxyStatus
import com.deelabs.untisplus.api.model.proxy.ProxyStreamTarget
import com.deelabs.untisplus.api.model.proxy.ProxySubscriptionTest
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChanges
import com.deelabs.untisplus.api.model.proxy.ProxyWebhookRequest
import com.deelabs.untisplus.api.model.proxy.ProxyWebhooks
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.jsonRpcApiUrlString
import com.deelabs.untisplus.data.database.entities.proxyBaseUrlString
import com.deelabs.untisplus.data.database.entities.proxySchoolNameValue
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Entry point for the `untis-proxy`'s own endpoints (`/status`, `/me`, and the
 * `/api/…` feature surface).
 *
 * The proxy authenticates these with the `JSESSIONID` cookie captured during the
 * intern login. On a `401` the session is silently re-established from the
 * stored app secret and the call is retried once; if that fails the caller sees
 * the error (and the existing re-login UI can take over).
 */
class ProxyRepository constructor(
	private val currentUserProvider: CurrentUserProvider,
	private val userDataApi: UserDataApi,
	private val proxyStatusApi: ProxyStatusApi,
	private val proxyHomeworkApi: ProxyHomeworkApi,
	private val proxyAbsenceApi: ProxyAbsenceApi,
	private val proxyAnalyticsApi: ProxyAnalyticsApi,
	private val proxyCalendarApi: ProxyCalendarApi,
	private val proxySubscriptionsApi: ProxySubscriptionsApi,
	private val proxyTimetableApi: ProxyTimetableApi,
) {
	private val _permissions = MutableStateFlow<ProxyPermissionsState>(ProxyPermissionsState.Unknown)
	private val _me = MutableStateFlow<ProxyMe?>(null)

	/** Last known proxy tier/permissions for the active user, or unknown. */
	val permissions: StateFlow<ProxyPermissionsState> = _permissions

	/** Full last known `/me` payload, including v2 capabilities. */
	val me: StateFlow<ProxyMe?> = _me

	private val compatibilityMutex = Mutex()
	private var compatibilityUserId: Long? = null
	private var teacherAbsencesSupported = false

	/** Whether proxy-only features apply to the current user. */
	val isAvailable: Boolean
		get() = currentUserProvider.currentUser?.usesUntisPlusProxy == true

	// --- §8 meta ---

	suspend fun status(): ProxyStatus = withSession { base, _ -> proxyStatusApi.status(base) }

	/**
	 * Cheap reachability pre-flight against the **public** `GET /status`, for a
	 * specific user and without touching the session. A reachable proxy always
	 * answers `{"status":"ok"}`; this returns `false` only when the request
	 * failed. Background change detection uses it to skip a full poll/stream
	 * attempt while the proxy is unreachable.
	 */
	suspend fun isReachable(user: User): Boolean {
		if (!user.usesUntisPlusProxy) return false
		return try {
			proxyStatusApi.status(user.proxyBaseUrlString)
			true
		} catch (e: Exception) {
			false
		}
	}

	suspend fun health(): ProxyHealth = withSession { base, _ -> proxyStatusApi.health(base) }

	/** Fetches `/me` and records the permissions/capabilities for feature gating. */
	suspend fun refreshMe(): ProxyMe = withSession { base, _ -> proxyStatusApi.me(base) }.also { recordMe(it) }

	/**
	 * Changes one of the active user's own capability opt-ins. The proxy
	 * validates the ceiling, so an enabled request can never grant access that
	 * the server or admin has not allowed.
	 */
	suspend fun updateCapability(capability: String, enabled: Boolean): ProxyMe =
		withSession { base, _ -> proxyStatusApi.updatePreferences(base, mapOf(capability to enabled)) }
			.also { recordMe(it) }

	private fun recordMe(me: ProxyMe) {
		_me.value = me
		_permissions.value = ProxyPermissionsState.Known(me.permissions)
	}

	/**
	 * Whether the active user's API host is a proxy that implements the
	 * `/me` endpoint. A successful JSON response is the compatibility signal;
	 * official Untis servers and old/custom proxies fail this probe.
	 *
	 * The result is cached per user so opening the drawer does not issue a
	 * request on every recomposition.
	 */
	suspend fun isTeacherAbsencesSupported(): Boolean = compatibilityMutex.withLock {
		val user = currentUserProvider.currentUser ?: return@withLock false
		if (!user.usesUntisPlusProxy) return@withLock false
		if (compatibilityUserId == user.id) return@withLock teacherAbsencesSupported

		val supported = try {
			refreshMe()
			true
		} catch (e: Exception) {
			false
		}
		compatibilityUserId = user.id
		teacherAbsencesSupported = supported
		supported
	}

	// --- teacher cancellation analytics ---

	suspend fun analyticsOptions() = withSession { base, school ->
		proxyAnalyticsApi.options(base, school)
	}

	suspend fun teacherCancellations(request: AnalyticsReportRequest) = withSession { base, school ->
		proxyAnalyticsApi.teacherCancellations(base, request, school)
	}

	// --- §3 homework ---

	suspend fun homeworkFlags(): ProxyHomeworkFlags = withSession { base, school ->
		proxyHomeworkApi.flags(base, school)
	}

	suspend fun setHomeworkDone(homeworkId: Long, done: Boolean): ProxyHomeworkFlag =
		withSession { base, school ->
			proxyHomeworkApi.setDone(base, homeworkId, done, school)
		}

	// --- §4 absence notes ---

	suspend fun absenceNotes() = withSession { base, school -> proxyAbsenceApi.notes(base, school) }

	suspend fun setAbsenceNote(absenceKey: Long, note: String) = withSession { base, school ->
		proxyAbsenceApi.setNote(base, absenceKey, note, school)
	}

	// --- §5 subscriptions ---

	/** Lists the timetable targets the active account may watch. */
	suspend fun notificationOptions(): ProxyCalendarOptions = withSession { base, school ->
		proxySubscriptionsApi.notificationOptions(base, school)
	}

	suspend fun webhooks(): ProxyWebhooks = withSession { base, school ->
		proxySubscriptionsApi.webhooks(base, school)
	}

	suspend fun createWebhook(request: ProxyWebhookRequest): ProxyCreated = withSession { base, school ->
		proxySubscriptionsApi.createWebhook(base, request, school)
	}

	suspend fun testWebhook(id: Long): ProxySubscriptionTest = withSession { base, school ->
		proxySubscriptionsApi.testWebhook(base, id, school)
	}

	suspend fun deleteWebhook(id: Long): ProxyDeleted = withSession { base, school ->
		proxySubscriptionsApi.deleteWebhook(base, id, school)
	}

	suspend fun ntfyTopics(): ProxyNtfyTopics = withSession { base, school ->
		proxySubscriptionsApi.ntfyTopics(base, school)
	}

	suspend fun createNtfy(request: ProxyNtfyRequest): ProxyCreated = withSession { base, school ->
		proxySubscriptionsApi.createNtfy(base, request, school)
	}

	suspend fun testNtfy(id: Long): ProxySubscriptionTest = withSession { base, school ->
		proxySubscriptionsApi.testNtfy(base, id, school)
	}

	suspend fun deleteNtfy(id: Long): ProxyDeleted = withSession { base, school ->
		proxySubscriptionsApi.deleteNtfy(base, id, school)
	}

	// --- §9 calendar ---

	suspend fun calendarToken(request: ProxyCalendarTokenRequest): ProxyCalendarToken =
		withSession { base, school ->
			proxyCalendarApi.token(base, request, school)
		}

	/** Lists the calendar targets the active account may subscribe to. */
	suspend fun calendarOptions(): ProxyCalendarOptions = withSession { base, school ->
		proxyCalendarApi.options(base, school)
	}

	/** Lists the active account's own calendar subscriptions. */
	suspend fun calendarTokens(): List<ProxyCalendarToken> = withSession { base, school ->
		proxyCalendarApi.tokens(base, school)
	}

	/** Re-scopes one of the active account's own subscriptions to [days]. */
	suspend fun calendarUpdateDays(token: String, days: Int): ProxyCalendarToken =
		withSession { base, school ->
			proxyCalendarApi.updateDays(base, token, days, school)
		}

	/** Replaces one of the active account's own subscription tokens. */
	suspend fun calendarRotate(token: String): ProxyCalendarToken = withSession { base, school ->
		proxyCalendarApi.rotate(base, token, school)
	}

	/** Revokes one of the active account's own calendar subscriptions. */
	suspend fun calendarRevoke(token: String) = withSession { base, school ->
		proxyCalendarApi.revoke(base, token, school)
	}

	/** The proxy's spreadsheet export for the same window as the ICS feed. */
	fun calendarCsvUrl(token: String): String? {
		val user = currentUserProvider.currentUser ?: return null
		if (!user.usesUntisPlusProxy) return null
		return "${user.proxyBaseUrlString.trimEnd('/')}/week/$token.csv"
	}

	// --- §7 change detection ---

	/** Polls for changes; `null` means "nothing new" (HTTP 304). */
	suspend fun timetableChanges(
		classId: Long,
		since: Long,
		sinceEvents: Long? = null
	): ProxyTimetableChanges? = withSession { base, school ->
		proxyTimetableApi.changes(base, classId, since, sinceEvents, school)
	}

	/** Opens the SSE change feed. Returns an empty flow when the proxy is unused. */
	fun timetableStream(target: ProxyStreamTarget = ProxyStreamTarget()): Flow<ProxyStreamEvent> {
		val user = currentUserProvider.currentUser ?: return flowOf()
		if (!user.usesUntisPlusProxy) return flowOf()
		return proxyTimetableApi.stream(user.proxyBaseUrlString, user.proxySchoolNameValue, target)
	}

	private suspend fun <T> withSession(block: suspend (baseUrl: String, school: String?) -> T): T {
		val user = currentUserProvider.currentUser
			?: throw IllegalStateException("No active user")
		check(user.usesUntisPlusProxy) { "UntisPlus proxy is not configured for this user" }
		val baseUrl = user.proxyBaseUrlString
		val school = user.proxySchoolNameValue

		return try {
			block(baseUrl, school)
		} catch (e: ClientRequestException) {
			if (e.response.status != HttpStatusCode.Unauthorized) throw e
			if (!refreshSession(user)) throw e
			block(baseUrl, school)
		}
	}

	/**
	 * Re-runs the intern login with the stored credentials, minting a new
	 * `JSESSIONID` in the shared cookie store. Returns false when the account has
	 * no stored secret to log in with (e.g. anonymous).
	 */
	private suspend fun refreshSession(user: User): Boolean {
		val key = user.key ?: return false
		return try {
			userDataApi.getUserData(user.jsonRpcApiUrlString, user.user, key)
			true
		} catch (e: Exception) {
			false
		}
	}

	sealed interface ProxyPermissionsState {
		data object Unknown : ProxyPermissionsState
		data class Known(val permissions: ProxyPermissions) : ProxyPermissionsState
	}
}
