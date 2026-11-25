package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.ProxyStreamTarget
import com.deelabs.untisplus.api.model.proxy.ProxyStreamTargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyTimetableApiTest {
	private val baseUrl = "https://api-untis.deelabs.tech/"

	@Test
	fun `personal stream defaults to school only`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/timetable/stream?school=Test%20School",
			timetableStreamUrl(baseUrl, "Test School", ProxyStreamTarget())
		)
	}

	@Test
	fun `teacher stream uses teacherId`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/timetable/stream?school=testschool&teacherId=42",
			timetableStreamUrl(
				baseUrl,
				"testschool",
				ProxyStreamTarget(ProxyStreamTargetType.TEACHER, 42)
			)
		)
	}

	@Test
	fun `class stream uses classId`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/timetable/stream?classId=5000",
			timetableStreamUrl(
				baseUrl,
				null,
				ProxyStreamTarget(ProxyStreamTargetType.CLASS, 5000)
			)
		)
	}

	@Test
	fun `school wide stream is explicit`() {
		assertEquals(
			"https://api-untis.deelabs.tech/api/timetable/stream?school=testschool&schoolWide=true",
			timetableStreamUrl(
				baseUrl,
				"testschool",
				ProxyStreamTarget(ProxyStreamTargetType.SCHOOL)
			)
		)
	}

	@Test
	fun `targets needing an id fall back to personal when the id is missing`() {
		val target = ProxyStreamTarget(ProxyStreamTargetType.ROOM, null).validated()
		assertEquals(ProxyStreamTargetType.PERSONAL, target.type)
		assertEquals(
			"https://api-untis.deelabs.tech/api/timetable/stream",
			timetableStreamUrl(baseUrl, null, target)
		)
	}
}
