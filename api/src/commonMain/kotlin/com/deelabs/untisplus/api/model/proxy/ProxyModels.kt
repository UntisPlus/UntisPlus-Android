package com.deelabs.untisplus.api.model.proxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the `untis-proxy`'s own endpoints (not stock WebUntis). The wire
 * contract is `docs/PROXY-API.md`.
 */

// --- §8 meta ---

@Serializable
data class ProxyStatus(
	val status: String,
	val mode: String? = null,
	val version: String? = null,
	@SerialName("uptime_sec") val uptimeSec: Long? = null
)

@Serializable
data class ProxyHealth(
	val status: String,
	@SerialName("uptime_sec") val uptimeSec: Long? = null,
	val schools: Int? = null,
	val degraded: Int? = null
)

@Serializable
data class ProxyMe(
	val username: String,
	val level: String,
	val permissions: ProxyPermissions = ProxyPermissions(),
	/** `/me` schema version. Older proxies omit it. */
	val v: Int? = null,
	val school: String? = null,
	val role: String? = null,
	val groups: List<String> = emptyList(),
	/** Sorted keys whose capability currently resolves to effective=true. */
	val effective: List<String> = emptyList(),
	/** All capabilities known to the proxy, keyed by capability key. */
	val capabilities: Map<String, ProxyCapabilityState> = emptyMap()
)

@Serializable
data class ProxyCapabilityState(
	@SerialName("server_enabled") val serverEnabled: Boolean = false,
	@SerialName("admin_allowed") val adminAllowed: Boolean = false,
	@SerialName("user_opted_in") val userOptedIn: Boolean = false,
	val effective: Boolean = false,
	val toggleable: Boolean = false,
	val source: String? = null
)

/** Body of `PATCH /me`: user capability preferences can only narrow access. */
@Serializable
data class ProxyMePreferences(
	val preferences: Map<String, Boolean>
)

@Serializable
data class ProxyPermissions(
	val recon: Boolean = false,
	val boosted: Boolean = false,
	val editor: Boolean = false
)

@Serializable
data class ProxyError(
	val error: String? = null
)

/** Targets accepted by the proxy's `/api/timetable/stream` SSE endpoint. */
enum class ProxyStreamTargetType(val storedName: String, val needsId: Boolean = false) {
	PERSONAL("PERSONAL"),
	CLASS("CLASS", needsId = true),
	PERSON("PERSON", needsId = true),
	TEACHER("TEACHER", needsId = true),
	ROOM("ROOM", needsId = true),
	SUBJECT("SUBJECT", needsId = true),
	SCHOOL("SCHOOL");

	companion object {
		fun fromStored(value: String?): ProxyStreamTargetType =
			entries.firstOrNull { it.storedName == value } ?: PERSONAL
	}
}

data class ProxyStreamTarget(
	val type: ProxyStreamTargetType = ProxyStreamTargetType.PERSONAL,
	val id: Long? = null
) {
	/** Falls back to the caller's own timetable when a required id is missing. */
	fun validated(): ProxyStreamTarget = if (type.needsId && (id == null || id <= 0)) {
		ProxyStreamTarget()
	} else {
		this
	}
}

// --- §3 homework ---

@Serializable
data class ProxyHomeworkFlag(
	val homeworkId: Long,
	val done: Boolean,
	val doneAt: String? = null
)

@Serializable
data class ProxyHomeworkFlags(
	val school: String? = null,
	val flags: List<ProxyHomeworkFlag> = emptyList()
)

@Serializable
data class ProxyHomeworkDoneRequest(
	val homeworkId: Long,
	val done: Boolean
)

// --- §4 absence notes ---

@Serializable
data class ProxyAbsenceNote(
	val absenceKey: Long,
	val note: String? = null,
	val updatedAt: String? = null
)

@Serializable
data class ProxyAbsenceNotes(
	val school: String? = null,
	val notes: List<ProxyAbsenceNote> = emptyList()
)

@Serializable
data class ProxyAbsenceNoteRequest(
	val absenceKey: Long,
	val note: String
)

// --- §5 subscriptions ---

/**
 * The element a webhook/ntfy subscription points at, using the same keys as the
 * calendar mint (`classId`, `personal`/`personId`, `teacherId`, `roomId`,
 * `subjectId`). At most one is set; none means the school-wide target, which is
 * privileged-only. `docs/PROXY-API.md` §5.
 */
data class ProxySubscriptionTarget(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null
)

@Serializable
data class ProxyWebhook(
	val id: Long,
	val school: String? = null,
	@SerialName("elementType") val elementType: String? = null,
	@SerialName("elementId") val elementId: Long? = null,
	val name: String? = null,
	val classId: Long? = null,
	val url: String,
	val createdBy: String? = null
)

@Serializable
data class ProxyWebhooks(
	val webhooks: List<ProxyWebhook> = emptyList()
)

@Serializable
data class ProxyWebhookRequest(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val url: String,
	val secret: String? = null
) {
	constructor(target: ProxySubscriptionTarget, url: String, secret: String? = null) : this(
		target.classId, target.personId, target.personal,
		target.teacherId, target.roomId, target.subjectId, url, secret
	)
}

@Serializable
data class ProxyNtfyTopic(
	val id: Long,
	val school: String? = null,
	@SerialName("elementType") val elementType: String? = null,
	@SerialName("elementId") val elementId: Long? = null,
	val name: String? = null,
	val classId: Long? = null,
	val topic: String,
	val baseUrl: String? = null,
	val createdBy: String? = null
)

@Serializable
data class ProxyNtfyTopics(
	val topics: List<ProxyNtfyTopic> = emptyList()
)

@Serializable
data class ProxyNtfyRequest(
	val classId: Long? = null,
	val personId: Long? = null,
	val personal: Boolean? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val topic: String,
	val baseUrl: String? = null
) {
	constructor(target: ProxySubscriptionTarget, topic: String, baseUrl: String? = null) : this(
		target.classId, target.personId, target.personal,
		target.teacherId, target.roomId, target.subjectId, topic, baseUrl
	)
}

@Serializable
data class ProxySubscriptionTest(
	val ok: Boolean = false,
	val status: Int? = null,
	val topic: String? = null,
	val server: String? = null
)

@Serializable
data class ProxyDeleted(
	val deleted: Boolean = false
)

@Serializable
data class ProxyCreated(
	val id: Long
)

// --- §7 change detection ---

@Serializable
data class ProxyChangeDigest(
	val title: String? = null,
	val summary: String? = null,
	val lines: List<String> = emptyList(),
	val changed: Int = 0,
	val added: Int = 0,
	val removed: Int = 0
)

/**
 * A single changed period. The row fields are deliberately **PascalCase** — the
 * proxy serialises a Go struct without JSON tags (`PROXY-API.md` §7.1).
 */
@Serializable
data class ProxyTimetableChangeRow(
	@SerialName("PeriodID") val periodId: Long,
	@SerialName("Kind") val kind: String,
	@SerialName("Start") val start: String,
	@SerialName("End") val end: String,
	@SerialName("Subject") val subject: String? = null,
	@SerialName("Room") val room: String? = null,
	@SerialName("Teacher") val teacher: String? = null,
	@SerialName("Description") val description: String? = null,
	@SerialName("ModVer") val modVer: Int? = null
) {
	companion object {
		const val KIND_ADDED = "ADDED"
		const val KIND_CHANGED = "CHANGED"
		const val KIND_REMOVED = "REMOVED"
	}
}

@Serializable
data class ProxyTimetableChanges(
	val school: String? = null,
	val classId: Long? = null,
	val since: Long = 0,
	val current: Long = 0,
	val eventVersion: Long? = null,
	val sinceEvents: Long? = null,
	val changes: List<ProxyTimetableChangeRow> = emptyList()
)

/**
 * Union of the three SSE event payloads on `/api/timetable/stream`
 * (`PROXY-API.md` §6). [event] is the SSE `event:` name.
 */
@Serializable
data class ProxySseEventPayload(
	val event: String? = null,
	val school: String? = null,
	val classId: Long? = null,
	val current: Long? = null,
	val version: Long? = null,
	val eventVersion: Long? = null,
	val reason: String? = null,
	val changes: List<ProxyTimetableChangeRow> = emptyList()
)

// --- §9 calendar ---

@Serializable
data class ProxyCalendarTokenRequest(
	val classId: Long? = null,
	val personal: Boolean? = null,
	val personId: Long? = null,
	val teacherId: Long? = null,
	val roomId: Long? = null,
	val subjectId: Long? = null,
	val timezone: String? = null,
	val days: Int? = null
)

@Serializable
data class ProxyCalendarToken(
	val type: String? = null,
	val id: Long? = null,
	val name: String? = null,
	val token: String,
	val url: String,
	val timezone: String? = null,
	val days: Int? = null,
	val horizon: String? = null,
	val created: String? = null,
	val lastUsed: String? = null,
	val createdBy: String? = null,
	val classId: Long? = null,
	val personId: Long? = null
)

/** One subscribable calendar target, as listed by `/api/calendar/options`. */
@Serializable
data class ProxyCalendarOption(
	val id: Long,
	val name: String = ""
)

/** The signed-in user's own personal timetable target. */
@Serializable
data class ProxyCalendarPerson(
	val personId: Long,
	val name: String = ""
)

@Serializable
data class ProxyCalendarOptions(
	val school: String? = null,
	val personal: ProxyCalendarPerson? = null,
	@SerialName("class") val classes: List<ProxyCalendarOption> = emptyList(),
	val teacher: List<ProxyCalendarOption> = emptyList(),
	val room: List<ProxyCalendarOption> = emptyList(),
	val subject: List<ProxyCalendarOption> = emptyList(),
	val technik: ProxyCalendarOption? = null
)

// --- teacher cancellation analytics ---

/** A school year configured on the proxy, usable as a report date shortcut. */
@Serializable
data class AnalyticsSchoolYear(
	val name: String,
	val from: String,
	val to: String
)

/**
 * An approved report target from `GET /api/analytics/options`.
 *
 * `mode` is one of `personal`, `teacher`, `student`, `class`, `room` or
 * `class-history`. Class targets carry [id]; history profiles carry [profileId].
 */
@Serializable
data class AnalyticsTarget(
	val mode: String,
	val name: String,
	val id: Long? = null,
	val profileId: String? = null,
	val dataSources: List<String> = emptyList()
) {
	val isClassTarget: Boolean
		get() = mode == "class" || mode == "class-history"
}

@Serializable
data class AnalyticsOptions(
	val school: String? = null,
	val schoolYears: List<AnalyticsSchoolYear> = emptyList(),
	val targets: List<AnalyticsTarget> = emptyList()
)

/**
 * Request body for `POST /api/analytics/teacher-cancellations`.
 *
 * Null fields must be omitted by the JSON configuration (see
 * [ApiClient.ANALYTICS_JSON]) because the endpoint sets
 * `additionalProperties: false`. In particular, leaving [dataSource] null lets
 * the proxy choose an approved source automatically.
 */
@Serializable
data class AnalyticsReportRequest(
	val mode: String = "personal",
	val from: String? = null,
	val to: String? = null,
	val teacherId: Long? = null,
	val studentId: Long? = null,
	val classId: Long? = null,
	val dataSource: String? = null,
	val room: String? = null,
	val profileId: String? = null,
	val schoolYear: String? = null,
	val minLessons: Int? = null,
	val anonymise: Boolean = false,
	val personalOtp: String? = null,
	val format: String = "json",
	val includeIrregular: Boolean = false,
	val excludeTeacherPrefixes: List<String>? = null
)

@Serializable
data class AnalyticsCounts(
	val scheduled: Int = 0,
	val cancelled: Int = 0,
	val notCancelled: Int = 0,
	val cancellationRate: Double? = null,
	val notCancelledRate: Double? = null
)

@Serializable
data class AnalyticsBucket(
	val key: String,
	val scheduled: Int = 0,
	val cancelled: Int = 0,
	val notCancelled: Int = 0,
	val cancellationRate: Double? = null,
	val notCancelledRate: Double? = null
)

@Serializable
data class AnalyticsElementReference(
	val id: String? = null,
	val name: String = ""
)

@Serializable
data class AnalyticsCoverPair(
	val original: AnalyticsElementReference,
	val teacher: AnalyticsElementReference
)

@Serializable
data class AnalyticsChangeCount(
	val id: String? = null,
	val name: String = "",
	val lessons: Int = 0,
	val cancelled: Int = 0,
	val covered: Int = 0
)

@Serializable
data class AnalyticsTeacher(
	val id: String? = null,
	val name: String,
	val rank: Int = 0,
	val scheduled: Int = 0,
	val cancelled: Int = 0,
	val notCancelled: Int = 0,
	val cancellationRate: Double? = null,
	val notCancelledRate: Double? = null,
	val byWeekday: List<AnalyticsBucket> = emptyList(),
	val byWeek: List<AnalyticsBucket> = emptyList(),
	val bySchoolYear: List<AnalyticsBucket> = emptyList(),
	val worstWeekdays: List<String> = emptyList()
)

@Serializable
data class AnalyticsWindow(
	val from: String,
	val to: String,
	val status: String,
	val reason: String? = null,
	val upstreamCode: Int? = null
)

@Serializable
data class AnalyticsLessonChange(
	val date: String,
	val start: String? = null,
	val end: String? = null,
	val cancelled: Boolean = false,
	val clash: String? = null,
	val subjects: List<AnalyticsElementReference> = emptyList(),
	val missedTeachers: List<AnalyticsElementReference> = emptyList(),
	val covers: List<AnalyticsCoverPair> = emptyList()
)

/**
 * The JSON report returned by the proxy. Only the fields consumed by the
 * sidebar tab are modelled explicitly; unknown fields are ignored by the
 * shared client JSON configuration.
 */
@Serializable
data class AnalyticsReport(
	val version: String? = null,
	val reportId: String? = null,
	val source: String? = null,
	val dataSource: String? = null,
	val room: String? = null,
	val from: String? = null,
	val to: String? = null,
	val generatedAt: String? = null,
	val complete: Boolean = false,
	val anonymised: Boolean = false,
	val windows: List<AnalyticsWindow> = emptyList(),
	val warnings: List<String> = emptyList(),
	val teachers: List<AnalyticsTeacher> = emptyList(),
	val teacherAssignmentTotals: AnalyticsCounts? = null,
	val uniqueLessons: Int? = null,
	val cancelledLessons: Int? = null,
	val coveredLessons: Int? = null,
	val clashingLessons: Int? = null,
	val lessonChanges: List<AnalyticsLessonChange> = emptyList(),
	val missedTeachers: List<AnalyticsChangeCount> = emptyList(),
	val missedSubjects: List<AnalyticsChangeCount> = emptyList(),
	val coverTeachers: List<AnalyticsChangeCount> = emptyList(),
	val byWeekday: List<AnalyticsBucket> = emptyList(),
	val byWeek: List<AnalyticsBucket> = emptyList(),
	val bySchoolYear: List<AnalyticsBucket> = emptyList(),
	val excludedIrregularLessons: Int? = null,
	val excludedUnknownTeacherLessons: Int? = null,
	val filteredTeachers: Int? = null,
	val unmappedSchoolYearLessons: Int? = null
)
