package com.deelabs.untisplus.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileQrExportTest {
	@Test
	fun buildUri_standardProfile_usesOfficialParameterOrderWithoutSkipFlag() {
		assertEquals(
			"untis://setschool?url=schuldorf.webuntis.com&school=schuldorf" +
				"&user=test.user&key=dummy-key&schoolNumber=4738900",
			ProfileQrExport.buildUri(
				url = "schuldorf.webuntis.com",
				school = "schuldorf",
				user = "test.user",
				key = "dummy-key",
				schoolNumber = "4738900",
			)
		)
	}

	@Test
	fun buildUri_optionalParameters_areEscapedAndAppendedAfterOfficialFive() {
		assertEquals(
			"untis://setschool?url=https%3A%2F%2Fexample.com%2FWebUntis%3Fa%3D1" +
				"&school=sch%26ool&user=u%20ser&key=a%26b%3Dc%20%C3%BC&schoolNumber=123" +
				"&anonymous=true&apiUrl=https%3A%2F%2Fplus.example&skipAppSecret=true",
			ProfileQrExport.buildUri(
				url = "https://example.com/WebUntis?a=1",
				school = "sch&ool",
				user = "u ser",
				key = "a&b=c ü",
				schoolNumber = "123",
				anonymous = true,
				apiUrl = "https://plus.example",
				skipAppSecret = true,
			)
		)
	}

	@Test
	fun buildUri_meaninglessOptionalParameters_areOmitted() {
		assertEquals(
			"untis://setschool?url=school.example&school=school&user=user&key=key",
			ProfileQrExport.buildUri(
				url = "school.example",
				school = "school",
				user = "user",
				key = "key",
				anonymous = false,
				apiUrl = "  ",
				skipAppSecret = false,
			)
		)
	}

	@Test
	fun parseUri_officialSample_readsAllFiveParameters() {
		val payload = requireNotNull(
			ProfileQrExport.parseUri(
				"untis://setschool?url=schuldorf.webuntis.com&school=schuldorf" +
					"&user=test.user&key=app-secret&schoolNumber=4738900"
			)
		)

		assertEquals("schuldorf.webuntis.com", payload.url)
		assertEquals("schuldorf", payload.school)
		assertEquals("test.user", payload.user)
		assertEquals("app-secret", payload.key)
		assertEquals("4738900", payload.schoolNumber)
		assertFalse(payload.anonymous)
		assertNull(payload.apiUrl)
		assertNull(payload.skipAppSecret)
	}

	@Test
	fun parseUri_roundTrip_decodesEscapedValues() {
		val uri = ProfileQrExport.buildUri(
			url = "https://school.example/WebUntis",
			school = "sch&ool",
			user = "u ser",
			key = "a&b=c ü",
			schoolNumber = "123",
		)
		val payload = requireNotNull(ProfileQrExport.parseUri(uri))

		assertEquals("https://school.example/WebUntis", payload.url)
		assertEquals("sch&ool", payload.school)
		assertEquals("u ser", payload.user)
		assertEquals("a&b=c ü", payload.key)
		assertEquals("123", payload.schoolNumber)
	}

	@Test
	fun shouldSkipAppSecret_officialFormatIsInferredExplicitFalseAndLegacyArePreserved() {
		val official = requireNotNull(
			ProfileQrExport.parseUri(
				"untis://setschool?url=school.example&school=s&user=u&key=app-secret"
			)
		)
		assertTrue(ProfileQrExport.shouldSkipAppSecret(official))

		val explicitFalse = requireNotNull(
			ProfileQrExport.parseUri(
				"untis://setschool?url=school.example&school=s&user=u&key=password&skipAppSecret=false"
			)
		)
		assertFalse(ProfileQrExport.shouldSkipAppSecret(explicitFalse))

		val legacy = requireNotNull(
			ProfileQrExport.parseUri("untis://setschool?school=s&user=u&key=password")
		)
		assertFalse(ProfileQrExport.shouldSkipAppSecret(legacy))
	}
}
