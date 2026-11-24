package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxySseEventPayload
import com.deelabs.untisplus.api.model.proxy.ProxyStreamTarget
import com.deelabs.untisplus.api.model.proxy.ProxyStreamTargetType
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChangeRow
import com.deelabs.untisplus.api.model.proxy.ProxyTimetableChanges
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.sse.sse
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json

/**
 * Timetable change detection (`docs/PROXY-API.md` §6/§7): the polling
 * `/api/timetable/changes` endpoint and the `/api/timetable/stream` SSE feed.
 */
open class ProxyTimetableApi(
	engineFactory: HttpClientEngineFactory<*>,
	config: ((HttpClientConfig<*>) -> Unit)? = null,
	private val jsonBlock: Json = ApiClient.DEFAULT_JSON
) : ApiClient(
	httpClientEngineFactory = engineFactory,
	httpClientConfig = config,
	jsonBlock = jsonBlock
) {
	/**
	 * Polls for changes since [since] (timetable version) / [sinceEvents]
	 * (student-event version). Returns `null` when the proxy answers `304 Not
	 * Modified` — i.e. there is nothing new.
	 */
	open suspend fun changes(
		baseUrl: String,
		classId: Long,
		since: Long,
		sinceEvents: Long? = null,
		school: String? = null
	): ProxyTimetableChanges? {
		val url = buildString {
			append(proxyUrl(baseUrl, "/api/timetable/changes"))
			append("?classId=").append(classId)
			append("&since=").append(since)
			sinceEvents?.let { append("&sinceEvents=").append(it) }
			school?.takeIf { it.isNotBlank() }?.let { append("&school=").append(it.encodeURLParameter()) }
		}
		return try {
			get(url).body()
		} catch (e: RedirectResponseException) {
			if (e.response.status == HttpStatusCode.NotModified) null else throw e
		}
	}

	/**
	 * Opens the SSE change feed for [target]. Each emitted [ProxyStreamEvent]
	 * corresponds to one `event:` frame; the flow stays open until the
	 * connection drops. The default target is the caller's own timetable.
	 */
	open fun stream(
		baseUrl: String,
		school: String? = null,
		target: ProxyStreamTarget = ProxyStreamTarget()
	): Flow<ProxyStreamEvent> = channelFlow {
		httpClient.sse(timetableStreamUrl(baseUrl, school, target.validated())) {
			incoming.collect { sse ->
				val data = sse.data ?: return@collect
				val payload = runCatching {
					jsonBlock.decodeFromString<ProxySseEventPayload>(data)
				}.getOrNull() ?: return@collect
				payload.toEvent()?.let { send(it) }
			}
		}
	}
}

/** Builds the stream URL, keeping query parameter order stable for tests. */
internal fun timetableStreamUrl(
	baseUrl: String,
	school: String?,
	target: ProxyStreamTarget
): String {
	val params = mutableListOf<String>()
	school?.takeIf { it.isNotBlank() }?.let { params += "school=${it.encodeURLParameter()}" }
	when (target.type) {
		ProxyStreamTargetType.PERSONAL -> Unit
		ProxyStreamTargetType.CLASS -> target.id?.let { params += "classId=$it" }
		ProxyStreamTargetType.PERSON -> target.id?.let { params += "personId=$it" }
		ProxyStreamTargetType.TEACHER -> target.id?.let { params += "teacherId=$it" }
		ProxyStreamTargetType.ROOM -> target.id?.let { params += "roomId=$it" }
		ProxyStreamTargetType.SUBJECT -> target.id?.let { params += "subjectId=$it" }
		ProxyStreamTargetType.SCHOOL -> params += "schoolWide=true"
	}
	val query = if (params.isEmpty()) "" else params.joinToString("&", prefix = "?")
	return "${baseUrl.trimEnd('/')}/api/timetable/stream$query"
}

/** A decoded SSE event from the proxy stream. */
sealed interface ProxyStreamEvent {
	data class Snapshot(
		val current: Long,
		val eventVersion: Long?,
		val changes: List<ProxyTimetableChangeRow>
	) : ProxyStreamEvent

	data class Change(
		val version: Long,
		val changes: List<ProxyTimetableChangeRow>
	) : ProxyStreamEvent

	data class StudentEvents(
		val eventVersion: Long,
		val reason: String?
	) : ProxyStreamEvent
}

private fun ProxySseEventPayload.toEvent(): ProxyStreamEvent? = when (event) {
	"snapshot" -> ProxyStreamEvent.Snapshot(
		current = current ?: version ?: 0,
		eventVersion = eventVersion,
		changes = changes
	)

	"change" -> ProxyStreamEvent.Change(
		version = version ?: current ?: 0,
		changes = changes
	)

	"student-events" -> ProxyStreamEvent.StudentEvents(
		eventVersion = eventVersion ?: 0,
		reason = reason
	)

	else -> null
}
