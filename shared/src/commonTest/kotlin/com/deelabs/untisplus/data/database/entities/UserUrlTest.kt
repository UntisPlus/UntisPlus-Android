package com.deelabs.untisplus.data.database.entities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserUrlTest {
	@Test
	fun normalizeAddsHttpsScheme() {
		assertEquals("https" to "schuldorf.webuntis.com", normalizeApiEndpoint("schuldorf.webuntis.com"))
	}

	@Test
	fun normalizeKeepsSchemeAndStripsPath() {
		assertEquals("http" to "foo.bar:8080", normalizeApiEndpoint("http://foo.bar:8080/path?x=1"))
	}

	@Test
	fun normalizeRejectsBlank() {
		assertNull(normalizeApiEndpoint("   "))
		assertNull(normalizeApiEndpoint("http://"))
	}

	@Test
	fun buildApiUrlAppendsWebUntis() {
		assertEquals("https://schuldorf.webuntis.com/WebUntis", buildApiUrlString("schuldorf.webuntis.com"))
	}

	@Test
	fun buildApiUrlKeepsSchemeAndHost() {
		assertEquals("http://host:8080/WebUntis", buildApiUrlString("http://host:8080/other"))
	}

	@Test
	fun buildJsonRpcAppendsEndpointAndSchool() {
		assertEquals(
			"https://x/WebUntis/jsonrpc_intern.do?school=Demo",
			buildJsonRpcApiUrlString("https://x/WebUntis", "Demo")
		)
	}

	@Test
	fun buildJsonRpcEncodesSchoolName() {
		assertEquals(
			"https://x/WebUntis/jsonrpc_intern.do?school=My%20School",
			buildJsonRpcApiUrlString("https://x/WebUntis", "My School")
		)
	}

	@Test
	fun queryParameterDecodes() {
		assertEquals("Abc", queryParameter("https://h/login?school=Abc", "school"))
		assertEquals("My School", queryParameter("https://h/login?school=My%20School", "school"))
		assertNull(queryParameter("https://h/login", "school"))
	}

	@Test
	fun resolveApiEndpointUsesCustomHost() {
		assertEquals(
			"https://proxy.example.com/WebUntis",
			resolveApiEndpoint("https://a/WebUntis", "proxy.example.com", useUntisPlusApi = false)
		)
	}

	@Test
	fun resolveApiEndpointUsesProxyHost() {
		assertEquals(
			"https://$UNTISPLUS_API_HOST/WebUntis",
			resolveApiEndpoint("https://a/WebUntis", null, useUntisPlusApi = true)
		)
	}

	@Test
	fun resolveApiEndpointLeavesDefaultAlone() {
		assertEquals(
			"https://a/WebUntis",
			resolveApiEndpoint("https://a/WebUntis", null, useUntisPlusApi = false)
		)
	}

	@Test
	fun detectsBetaApiHostWithOrWithoutScheme() {
		assertTrue(isUntisPlusBetaApiHost(UNTISPLUS_BETA_API_HOST))
		assertTrue(isUntisPlusBetaApiHost("https://$UNTISPLUS_BETA_API_HOST/WebUntis"))
		assertFalse(isUntisPlusBetaApiHost(UNTISPLUS_API_HOST))
		assertFalse(isUntisPlusBetaApiHost(null))
	}
}
