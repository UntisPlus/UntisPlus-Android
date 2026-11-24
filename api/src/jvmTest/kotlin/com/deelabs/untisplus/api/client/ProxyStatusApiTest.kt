package com.deelabs.untisplus.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStatusApiTest {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

	private fun apiFor(
		handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
	) = ProxyStatusApi(
		HttpClient(MockEngine(handler)) {
			install(ContentNegotiation) { json(json, contentType = ContentType.Application.Json) }
		}
	)

	private val meJson = """
		{
			"v": 2,
			"username": "dee",
			"school": "testschool",
			"role": "teacher",
			"groups": ["students", "beta"],
			"level": "boosted+editor",
			"permissions": {"recon": false, "boosted": true, "editor": true},
			"effective": ["homework.own.manage", "analytics.teacher.view"],
			"capabilities": {
				"homework.own.manage": {
					"server_enabled": true, "admin_allowed": true, "user_opted_in": true,
					"effective": true, "toggleable": true, "source": "group"
				},
				"analytics.teacher.view": {
					"server_enabled": true, "admin_allowed": true, "user_opted_in": false,
					"effective": true, "toggleable": false, "source": "implicit"
				}
			}
		}
	""".trimIndent()

	@Test
	fun `me parses v2 capabilities`() = runBlocking {
		var path: String? = null
		val api = apiFor { request ->
			path = request.url.encodedPath
			respond(
				content = meJson,
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val me = api.me("https://api-untis.deelabs.tech")

		assertEquals("/me", path)
		assertEquals(2, me.v)
		assertEquals("teacher", me.role)
		assertEquals(listOf("students", "beta"), me.groups)
		assertEquals(2, me.effective.size)
		assertEquals("group", me.capabilities["homework.own.manage"]?.source)
		assertTrue(me.capabilities.getValue("homework.own.manage").toggleable)
		assertTrue(me.capabilities.getValue("analytics.teacher.view").effective)
		assertFalse(me.capabilities.getValue("analytics.teacher.view").toggleable)
	}

	@Test
	fun `updatePreferences patches me and parses the response`() = runBlocking {
		var method: HttpMethod? = null
		var path: String? = null
		var body: String? = null
		val api = apiFor { request ->
			method = request.method
			path = request.url.encodedPath
			body = (request.body as? OutgoingContent.ByteArrayContent)
				?.bytes()
				?.toString(Charsets.UTF_8)
			respond(
				content = meJson,
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}

		val me = api.updatePreferences(
			"https://api-untis.deelabs.tech",
			mapOf("pool.timetable.share" to false)
		)

		assertEquals(HttpMethod.Patch, method)
		assertEquals("/me", path)
		assertTrue(body?.contains("\"pool.timetable.share\":false") == true)
		assertEquals(2, me.v)
		assertEquals("boosted+editor", me.level)
	}
}
