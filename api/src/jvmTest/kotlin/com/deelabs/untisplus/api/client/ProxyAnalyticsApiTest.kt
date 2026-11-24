package com.deelabs.untisplus.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import com.deelabs.untisplus.api.model.proxy.AnalyticsReportRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAnalyticsApiTest {
	private fun apiFor(
		handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
	) = ProxyAnalyticsApi(
		HttpClient(MockEngine { request -> handler(request) }) {
			install(ContentNegotiation) {
				json(ApiClient.ANALYTICS_JSON)
			}
		}
	)

	@Test
	fun `analytics request omits unset optional fields`() {
		val body = ApiClient.ANALYTICS_JSON.encodeToString(
			AnalyticsReportRequest.serializer(),
			AnalyticsReportRequest(
				mode = "class",
				classId = 5000,
				from = "2026-10-01",
				to = "2026-10-07"
			)
		)

		assertTrue(body.contains("\"mode\": \"class\""))
		assertTrue(body.contains("\"classId\": 5000"))
		assertTrue(body.contains("\"from\": \"2026-10-01\""))
		assertFalse(body.contains("\"room\""))
		assertFalse(body.contains("\"teacherId\""))
		assertFalse(body.contains("\"schoolYear\""))
		assertFalse(body.contains("\"dataSource\""))
	}

	@Test
	fun `analytics request includes an explicit data source`() {
		val body = ApiClient.ANALYTICS_JSON.encodeToString(
			AnalyticsReportRequest.serializer(),
			AnalyticsReportRequest(
				mode = "teacher",
				teacherId = 42,
				from = "2026-10-01",
				to = "2026-10-07",
				dataSource = "own-teacher"
			)
		)

		assertTrue(body.contains("\"dataSource\": \"own-teacher\""))
	}

	@Test
	fun `options parses class targets and school years`() = runBlocking {
		var path: String? = null
		var school: String? = null

		val api = apiFor { request ->
			path = request.url.encodedPath
			school = request.url.parameters["school"]
			respond(
				content = """
					{
					  "school": "Testschool",
					  "schoolYears": [
					    {"name": "2025/2026", "from": "2025-08-01", "to": "2026-07-31"}
					  ],
					  "targets": [
					    {"mode": "class", "name": "10b", "id": 5000, "dataSources": ["shared-class"]},
					    {"mode": "class-history", "name": "10b (old)", "profileId": "my-class-history", "dataSources": ["reconstruction"]}
					  ]
					}
				""".trimIndent(),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val options = api.options("https://beta-untis.deelabs.tech", "Test School")

		assertEquals("/api/analytics/options", path)
		assertEquals("Test School", school)
		assertEquals("Testschool", options.school)
		assertEquals(1, options.schoolYears.size)
		assertEquals("2025/2026", options.schoolYears.single().name)
		assertEquals(2, options.targets.size)
		assertTrue(options.targets.all { it.isClassTarget })
		assertEquals(5000L, options.targets.first().id)
		assertEquals("my-class-history", options.targets.last().profileId)
	}

	@Test
	fun `teacher cancellations posts a report request and parses lesson rows`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null

		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			respond(
				content = """
					{
					  "version": "1",
					  "reportId": "r-1",
					  "source": "class",
					  "dataSource": "shared-class",
					  "from": "2026-10-01",
					  "to": "2026-10-07",
					  "complete": true,
					  "anonymised": false,
					  "windows": [{"from": "2026-10-01", "to": "2026-10-07", "status": "ok"}],
					  "warnings": [],
					  "teachers": [
					    {"id": "42", "name": "Mr Smith", "rank": 1, "scheduled": 10, "cancelled": 3,
					     "notCancelled": 7, "cancellationRate": 30.0, "notCancelledRate": 70.0}
					  ],
					  "teacherAssignmentTotals": {"scheduled": 10, "cancelled": 3, "notCancelled": 7,
					                              "cancellationRate": 30.0, "notCancelledRate": 70.0},
					  "uniqueLessons": 10,
					  "cancelledLessons": 3,
					  "coveredLessons": 1,
					  "lessonChanges": [
					    {"date": "2026-10-02", "start": "09:00", "end": "09:45", "cancelled": true,
					     "clash": "no_overlap_observed",
					     "subjects": [{"id": "7", "name": "Math"}],
					     "missedTeachers": [{"id": "42", "name": "Mr Smith"}],
					     "covers": []}
					  ],
					  "missedTeachers": [{"id": "42", "name": "Mr Smith", "lessons": 3, "cancelled": 3, "covered": 0}],
					  "missedSubjects": [],
					  "coverTeachers": []
					}
				""".trimIndent(),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val report = api.teacherCancellations(
			"https://beta-untis.deelabs.tech",
			AnalyticsReportRequest(mode = "class", classId = 5000, from = "2026-10-01", to = "2026-10-07"),
			"Test School"
		)

		assertEquals(HttpMethod.Post, method)
		assertEquals("/api/analytics/teacher-cancellations", path)
		assertEquals("r-1", report.reportId)
		assertEquals(3, report.cancelledLessons)
		assertEquals(1, report.coveredLessons)
		assertEquals("Mr Smith", report.lessonChanges.single().missedTeachers.single().name)
		assertEquals(30.0, report.teachers.single().cancellationRate)
	}
}
