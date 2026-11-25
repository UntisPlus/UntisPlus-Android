package com.deelabs.untisplus.ical

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Minimal, dependency-free iCalendar (RFC 5545) reader.
 *
 * Only the subset needed to render subscribed calendars is supported: `VEVENT`
 * blocks with `DTSTART`/`DTEND`/`DURATION`, `UID`/`SUMMARY`/`DESCRIPTION`/
 * `LOCATION`, `RRULE` (a bounded subset), `EXDATE` and `RECURRENCE-ID`. Anything
 * unsupported is skipped rather than failing the whole file.
 */
object ICalParser {

	private val DURATION_REGEX = Regex(
		"^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$",
		RegexOption.IGNORE_CASE
	)

	/** Parses a calendar file into its (unexpanded) `VEVENT`s. */
	fun parse(text: String): List<ICalRecurringEvent> {
		val events = ArrayList<ICalRecurringEvent>()
		var current: MutableMap<String, Property>? = null

		for (line in unfold(text)) {
			val property = parseProperty(line) ?: continue
			when {
				property.name == "BEGIN" && property.value.equals("VEVENT", ignoreCase = true) ->
					current = LinkedHashMap()

				property.name == "END" && property.value.equals("VEVENT", ignoreCase = true) -> {
					current?.let { toEvent(it) }?.let(events::add)
					current = null
				}

				current != null -> current!!.putIfAbsent(property.name, property)
			}
		}

		return events
	}

	/** Unfolds content lines: a line continuation starts with a single space/tab. */
	internal fun unfold(text: String): List<String> {
		val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
		val result = ArrayList<String>(lines.size)
		for (line in lines) {
			if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
				result[result.lastIndex] = result.last() + line.substring(1)
			} else if (line.isNotBlank()) {
				result.add(line)
			}
		}
		return result
	}

	internal data class Property(
		val name: String,
		val params: Map<String, String>,
		val value: String,
	)

	internal fun parseProperty(line: String): Property? {
		var inQuotes = false
		var colonIndex = -1
		for (i in line.indices) {
			when (line[i]) {
				'"' -> inQuotes = !inQuotes
				':' -> if (!inQuotes) {
					colonIndex = i
					break
				}
			}
		}
		if (colonIndex < 0) return null

		val head = line.substring(0, colonIndex)
		val value = unescape(line.substring(colonIndex + 1))
		val segments = splitUnquoted(head, ';')
		val name = segments.firstOrNull()?.trim()?.uppercase() ?: return null

		val params = LinkedHashMap<String, String>()
		for (segment in segments.drop(1)) {
			val index = segment.indexOf('=')
			if (index <= 0) continue
			val key = segment.substring(0, index).trim().uppercase()
			val raw = segment.substring(index + 1).trim().removeSurrounding("\"")
			params[key] = raw
		}

		return Property(name, params, value)
	}

	private fun splitUnquoted(value: String, separator: Char): List<String> {
		val result = ArrayList<String>()
		val current = StringBuilder()
		var inQuotes = false
		for (c in value) {
			when {
				c == '"' -> {
					inQuotes = !inQuotes
					current.append(c)
				}

				c == separator && !inQuotes -> {
					result.add(current.toString())
					current.setLength(0)
				}

				else -> current.append(c)
			}
		}
		result.add(current.toString())
		return result
	}

	private fun toEvent(properties: Map<String, Property>): ICalRecurringEvent? {
		val startProperty = properties["DTSTART"] ?: return null
		val parsedStart = parseDateTime(startProperty) ?: return null
		val allDay = parsedStart.second

		val endProperty = properties["DTEND"]
		val end = when {
			endProperty != null -> {
				val parsedEnd = parseDateTime(endProperty)
				val rawEnd = parsedEnd?.first ?: defaultEnd(parsedStart.first, allDay)
				// All-day DTEND is exclusive; convert to an inclusive end.
				if (allDay && rawEnd > parsedStart.first) {
					LocalDateTime(rawEnd.date.minus(1, DateTimeUnit.DAY), END_OF_DAY)
				} else {
					rawEnd
				}
			}

			properties["DURATION"] != null ->
				parseDuration(properties.getValue("DURATION").value)
					?.let { parsedStart.first.plusPeriodAtUtc(it) }
					?: defaultEnd(parsedStart.first, allDay)

			else -> defaultEnd(parsedStart.first, allDay)
		}

		return ICalRecurringEvent(
			uid = properties["UID"]?.value.orEmpty(),
			summary = properties["SUMMARY"]?.value.orEmpty(),
			description = properties["DESCRIPTION"]?.value?.takeIf { it.isNotBlank() },
			location = properties["LOCATION"]?.value?.takeIf { it.isNotBlank() },
			start = parsedStart.first,
			end = end,
			allDay = allDay,
			rrule = properties["RRULE"]?.value?.let(RecurrenceRuleParser::parse),
			exDates = properties["EXDATE"]?.let { exdate ->
				splitUnquoted(exdate.value, ',').mapNotNull { parseDateTimeValue(it, exdate.params) }
			}.orEmpty(),
			recurrenceId = properties["RECURRENCE-ID"]?.let { parseDateTime(it)?.first },
		)
	}

	private fun defaultEnd(start: LocalDateTime, allDay: Boolean): LocalDateTime =
		if (allDay) LocalDateTime(start.date, END_OF_DAY)
		else start.plusPeriodAtUtc(DateTimePeriod(hours = 1, nanoseconds = -1))

	private fun parseDateTime(property: Property): Pair<LocalDateTime, Boolean>? {
		val allDay = property.params["VALUE"].equals("DATE", ignoreCase = true) ||
			property.value.trim().length == 8
		return parseDateTimeValue(property.value, property.params)?.let { it to allDay }
	}

	private fun parseDateTimeValue(raw: String, params: Map<String, String>): LocalDateTime? {
		val value = raw.trim()
		if (value.isEmpty()) return null

		val isUtc = value.endsWith("Z", ignoreCase = true)
		val core = if (isUtc) value.dropLast(1) else value

		if (core.length == 8 && core.all(Char::isDigit)) {
			return runCatching { LocalDateTime(parseBasicDate(core), LocalTime(0, 0)) }.getOrNull()
		}
		if (core.length != 15 || core[8] != 'T') return null

		val local = runCatching {
			LocalDateTime(
				parseBasicDate(core.substring(0, 8)),
				LocalTime(
					core.substring(9, 11).toInt(),
					core.substring(11, 13).toInt(),
					core.substring(13, 15).toInt(),
				)
			)
		}.getOrNull() ?: return null

		val zone = when {
			isUtc -> TimeZone.UTC
			else -> params["TZID"]?.let { runCatching { TimeZone.of(it) }.getOrNull() }
		} ?: return local

		return local.toInstant(zone).toLocalDateTime(TimeZone.currentSystemDefault())
	}

	private fun parseDuration(value: String): DateTimePeriod? {
		val match = DURATION_REGEX.matchEntire(value.trim()) ?: return null
		val sign = if (match.groupValues[1] == "-") -1 else 1
		val weeks = match.groupValues[2].toIntOrNull() ?: 0
		val days = (match.groupValues[3].toIntOrNull() ?: 0) + weeks * 7
		return DateTimePeriod(
			days = sign * days,
			hours = sign * (match.groupValues[4].toIntOrNull() ?: 0),
			minutes = sign * (match.groupValues[5].toIntOrNull() ?: 0),
			seconds = sign * (match.groupValues[6].toIntOrNull() ?: 0),
		)
	}

	private fun parseBasicDate(value: String): LocalDate =
		LocalDate(
			value.substring(0, 4).toInt(),
			value.substring(4, 6).toInt(),
			value.substring(6, 8).toInt(),
		)

	private fun unescape(value: String): String {
		if (value.indexOf('\\') < 0) return value
		val builder = StringBuilder(value.length)
		var i = 0
		while (i < value.length) {
			val c = value[i]
			if (c == '\\' && i + 1 < value.length) {
				when (val next = value[i + 1]) {
					'n', 'N' -> builder.append('\n')
					',' -> builder.append(',')
					';' -> builder.append(';')
					'\\' -> builder.append('\\')
					else -> builder.append(next)
				}
				i += 2
			} else {
				builder.append(c)
				i++
			}
		}
		return builder.toString()
	}
}

internal object RecurrenceRuleParser {
	fun parse(value: String): RecurrenceRule? {
		val parts = HashMap<String, String>()
		for (segment in value.split(';')) {
			val index = segment.indexOf('=')
			if (index > 0) parts[segment.substring(0, index).uppercase()] = segment.substring(index + 1)
		}

		val frequency = when (parts["FREQ"]?.uppercase()) {
			"DAILY" -> RecurrenceFrequency.DAILY
			"WEEKLY" -> RecurrenceFrequency.WEEKLY
			"MONTHLY" -> RecurrenceFrequency.MONTHLY
			"YEARLY" -> RecurrenceFrequency.YEARLY
			else -> return null
		}

		return RecurrenceRule(
			frequency = frequency,
			interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
			count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 },
			until = parts["UNTIL"]?.let(::parseUntil),
			byDay = parts["BYDAY"].orEmpty().split(',').mapNotNull(::parseByDay),
			byMonthDay = parts["BYMONTHDAY"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
			byMonth = parts["BYMONTH"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
		)
	}

	private fun parseUntil(value: String): LocalDateTime? {
		val core = value.trim().removeSuffix("Z")
		return runCatching {
			if (core.length == 8) {
				LocalDateTime(
					LocalDate(core.substring(0, 4).toInt(), core.substring(4, 6).toInt(), core.substring(6, 8).toInt()),
					END_OF_DAY,
				)
			} else {
				LocalDateTime(
					LocalDate(core.substring(0, 4).toInt(), core.substring(4, 6).toInt(), core.substring(6, 8).toInt()),
					LocalTime(
						core.substring(9, 11).toInt(),
						core.substring(11, 13).toInt(),
						core.substring(13, 15).toInt(),
					)
				)
			}
		}.getOrNull()
	}

	private fun parseByDay(value: String): DayOfWeek? =
		when (value.trim().takeLast(2).uppercase()) {
			"MO" -> DayOfWeek.MONDAY
			"TU" -> DayOfWeek.TUESDAY
			"WE" -> DayOfWeek.WEDNESDAY
			"TH" -> DayOfWeek.THURSDAY
			"FR" -> DayOfWeek.FRIDAY
			"SA" -> DayOfWeek.SATURDAY
			"SU" -> DayOfWeek.SUNDAY
			else -> null
		}
}
