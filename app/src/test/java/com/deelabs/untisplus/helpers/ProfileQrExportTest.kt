package com.deelabs.untisplus.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileQrExportTest {
	@Test
	fun buildUri_minimalPayload_encodesRequiredParameters() {
		assertEquals(
			"untis://setschool?school=My%20School&user=user&key=secret",
			ProfileQrExport.buildUri(
				school = "My School",
				user = "user",
				key = "secret",
			)
		)
	}

	@Test
	fun buildUri_optionalParameters_areEscapedAndOrdered() {
		assertEquals(
			"untis://setschool?school=sch%26ool&user=u%20ser&key=a%26b%3Dc%20%C3%BC" +
				"&anonymous=true" +
				"&apiUrl=https%3A%2F%2Fexample.com%2FWebUntis%3Fa%3D1%26b%3D2" +
				"&skipAppSecret=true",
			ProfileQrExport.buildUri(
				school = "sch&ool",
				user = "u ser",
				key = "a&b=c ü",
				anonymous = true,
				apiUrl = "https://example.com/WebUntis?a=1&b=2",
				skipAppSecret = true,
			)
		)
	}

	@Test
	fun buildUri_meaninglessOptionalParameters_areOmitted() {
		assertEquals(
			"untis://setschool?school=school&user=user&key=key",
			ProfileQrExport.buildUri(
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
	fun buildUri_skipAppSecretWithoutKey_isOmitted() {
		assertEquals(
			"untis://setschool?school=school&user=user",
			ProfileQrExport.buildUri(
				school = "school",
				user = "user",
				key = null,
				skipAppSecret = true,
			)
		)
	}
}
