package com.deelabs.untisplus.ical

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Expands recurring [ICalRecurringEvent]s into concrete [ICalEvent] instances
 * within a requested date range. Only the recurrence rules commonly emitted by
 * calendar exports are supported; anything else is treated as a single event.
 */
object RecurrenceExpander {

	private const val MAX_INSTANCES = 1000

	fun expand(events: List<ICalRecurringEvent>, from: LocalDate, to: LocalDate): List<ICalEvent> {
		if (events.isEmpty()) return emptyList()

		val result = ArrayList<ICalEvent>()
		val overrides = events.filter { it.recurrenceId != null }

		for (event in events) {
			if (event.recurrenceId != null) continue

			val rule = event.rrule
			if (rule == null) {
				if (overlaps(event.start, event.end, from, to)) result.add(event.toInstance(event.start))
				continue
			}

			val exDates = event.exDates.toHashSet()
			var index = 0
			for (date in occurrences(event, rule, from, to)) {
				if (index++ >= MAX_INSTANCES) break
				val start = LocalDateTime(date, event.start.time)
				if (start in exDates) continue
				if (overrides.any { it.uid == event.uid && it.recurrenceId == start }) continue
				val instance = event.toInstance(start)
				if (overlaps(instance.start, instance.end, from, to)) result.add(instance)
			}
		}

		for (override in overrides) {
			if (overlaps(override.start, override.end, from, to)) result.add(override.toInstance(override.start))
		}

		return result.sortedBy { it.start }
	}

	private fun overlaps(start: LocalDateTime, end: LocalDateTime, from: LocalDate, to: LocalDate): Boolean {
		val rangeStart = LocalDateTime(from, LocalTime(0, 0))
		val rangeEnd = LocalDateTime(to, END_OF_DAY)
		return end >= rangeStart && start <= rangeEnd
	}

	private fun occurrences(
		event: ICalRecurringEvent,
		rule: RecurrenceRule,
		from: LocalDate,
		to: LocalDate,
	): List<LocalDate> {
		val result = ArrayList<LocalDate>()
		val base = event.start.date
		val until = rule.until?.date ?: to.plus(1, DateTimeUnit.YEAR)
		val limit = if (until < to) until else to.plus(1, DateTimeUnit.DAY)
		val countLimited = rule.count != null

		fun add(date: LocalDate): Boolean {
			if (date < base || date > limit) return false
			result.add(date)
			return result.size < MAX_INSTANCES
		}

		when (rule.frequency) {
			RecurrenceFrequency.DAILY -> {
				var date = if (countLimited) base else jumpDays(base, from, rule.interval)
				while (date <= limit && result.size < MAX_INSTANCES) {
					if (!add(date)) break
					date = date.plus(rule.interval, DateTimeUnit.DAY)
				}
			}

			RecurrenceFrequency.WEEKLY -> {
				val days = rule.byDay.ifEmpty { listOf(base.dayOfWeek) }.sortedBy { it.isoDayNumber }
				val anchor = base.minus(
					(base.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber + 7) % 7,
					DateTimeUnit.DAY,
				)
				var week = if (countLimited) 0 else jumpWeeks(anchor, from, rule.interval)
				while (result.size < MAX_INSTANCES) {
					val weekMonday = anchor.plus(week * rule.interval, DateTimeUnit.WEEK)
					if (weekMonday > limit) break
					for (day in days) {
						val date = weekMonday.plus(
							(day.isoDayNumber - weekMonday.dayOfWeek.isoDayNumber + 7) % 7,
							DateTimeUnit.DAY,
						)
						if (!add(date)) {
							if (date > limit) break
						}
					}
					week++
				}
			}

			RecurrenceFrequency.MONTHLY -> {
				val monthDays = rule.byMonthDay.ifEmpty { listOf(base.dayOfMonth) }
				val startMonth = YearMonth(base.year, base.monthNumber)
				var month = if (countLimited) 0 else jumpMonths(startMonth, from, rule.interval)
				while (result.size < MAX_INSTANCES) {
					val yearMonth = startMonth.plusMonths(month * rule.interval)
					if (yearMonth.atEndOfMonth() > limit) break
					val length = yearMonth.lengthOfMonth
					for (dom in monthDays) {
						if (dom !in 1..length) continue
						if (!add(yearMonth.atDay(dom)) && yearMonth.atDay(dom) > limit) break
					}
					month++
				}
			}

			RecurrenceFrequency.YEARLY -> {
				val months = rule.byMonth.ifEmpty { listOf(base.monthNumber) }
				val days = rule.byMonthDay.ifEmpty { listOf(base.dayOfMonth) }
				var year = if (countLimited) 0 else maxOf(0, from.year - base.year)
				while (result.size < MAX_INSTANCES) {
					val y = base.year + year * rule.interval
					if (y > limit.year) break
					for (m in months) for (dom in days) {
						val ym = YearMonth(y, m)
						if (dom !in 1..ym.lengthOfMonth) continue
						if (!add(LocalDate(y, m, dom))) break
					}
					year++
				}
			}
		}

		return if (countLimited) result.take(rule.count!!) else result
	}

	private fun jumpDays(base: LocalDate, from: LocalDate, interval: Int): LocalDate {
		if (from <= base) return base
		val elapsed = base.daysUntil(from)
		val steps = elapsed / interval
		return base.plus(steps * interval, DateTimeUnit.DAY)
	}

	private fun jumpWeeks(anchor: LocalDate, from: LocalDate, interval: Int): Int {
		if (from <= anchor) return 0
		val elapsedWeeks = anchor.daysUntil(from) / 7
		return (elapsedWeeks / interval).coerceAtLeast(0)
	}

	private fun jumpMonths(baseMonth: YearMonth, from: LocalDate, interval: Int): Int {
		val target = YearMonth(from.year, from.monthNumber)
		if (target <= baseMonth) return 0
		val elapsed = (target.year * 12 + target.month) - (baseMonth.year * 12 + baseMonth.month)
		return (elapsed / interval).coerceAtLeast(0)
	}

	private fun ICalRecurringEvent.toInstance(start: LocalDateTime): ICalEvent {
		val durationSeconds = (
			this.end.toInstant(TimeZone.UTC).epochSeconds -
				this.start.toInstant(TimeZone.UTC).epochSeconds
			).coerceAtLeast(0)
		return ICalEvent(
			uid = uid,
			summary = summary,
			description = description,
			location = location,
			start = start,
			end = if (allDay) LocalDateTime(start.date, END_OF_DAY)
			else start.plusPeriodAtUtc(DateTimePeriod(seconds = durationSeconds.toInt())),
			allDay = allDay,
		)
	}
}

/**
 * Minimal replacement for `java.time.YearMonth`, which has no kotlinx-datetime
 * equivalent. [month] is 1-based.
 */
private data class YearMonth(val year: Int, val month: Int) {
	val lengthOfMonth: Int
		get() = LocalDate(year, month, 1)
			.plus(1, DateTimeUnit.MONTH)
			.minus(1, DateTimeUnit.DAY)
			.dayOfMonth

	fun atEndOfMonth(): LocalDate = LocalDate(year, month, lengthOfMonth)

	fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

	fun plusMonths(months: Int): YearMonth {
		val total = year * 12 + (month - 1) + months
		return YearMonth(total.floorDiv(12), total.mod(12) + 1)
	}

	operator fun compareTo(other: YearMonth): Int =
		(year * 12 + month).compareTo(other.year * 12 + other.month)
}
