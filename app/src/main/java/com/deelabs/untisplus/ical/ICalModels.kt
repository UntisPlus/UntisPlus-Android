package com.deelabs.untisplus.ical

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Inclusive end-of-day, used to mark all-day ranges. */
internal val END_OF_DAY: LocalTime = LocalTime(23, 59, 59, 999_999_999)

/**
 * Adds [period] to this wall-clock date-time as a fixed-length amount.
 *
 * kotlinx-datetime deliberately exposes no `LocalDateTime` arithmetic (DST makes
 * it ambiguous), so the conversion is routed through `Instant` at UTC, which
 * keeps the addition linear and DST-independent.
 */
internal fun LocalDateTime.plusPeriodAtUtc(period: DateTimePeriod): LocalDateTime =
	toInstant(TimeZone.UTC).plus(period, TimeZone.UTC).toLocalDateTime(TimeZone.UTC)

/**
 * A single (already expanded) calendar entry. Recurring `VEVENT`s are expanded
 * into one [ICalEvent] per occurrence by [RecurrenceExpander].
 */
data class ICalEvent(
	val uid: String,
	val summary: String,
	val description: String?,
	val location: String?,
	val start: LocalDateTime,
	val end: LocalDateTime,
	val allDay: Boolean,
)

/**
 * The unexpanded representation of a `VEVENT` as read from the calendar file.
 * [rrule] and [exDates] describe the recurrence; [overrideEvents] holds the
 * concrete instances supplied via separate `RECURRENCE-ID` events.
 */
data class ICalRecurringEvent(
	val uid: String,
	val summary: String,
	val description: String?,
	val location: String?,
	val start: LocalDateTime,
	val end: LocalDateTime,
	val allDay: Boolean,
	val rrule: RecurrenceRule? = null,
	val exDates: List<LocalDateTime> = emptyList(),
	val recurrenceId: LocalDateTime? = null,
)

enum class RecurrenceFrequency {
	DAILY, WEEKLY, MONTHLY, YEARLY
}

data class RecurrenceRule(
	val frequency: RecurrenceFrequency,
	val interval: Int = 1,
	val count: Int? = null,
	val until: LocalDateTime? = null,
	val byDay: List<DayOfWeek> = emptyList(),
	val byMonthDay: List<Int> = emptyList(),
	val byMonth: List<Int> = emptyList(),
)
