package com.deelabs.untisplus.helpers

import com.deelabs.untisplus.api.model.untis.SchoolInfo
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.normalizeApiEndpoint
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.login.datainput.LoginDataInputViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileQrExportTest {
	companion object {
		// Local fixtures only: no accounts, credentials or live endpoints.
		private const val FIXTURE_URL = "schuldorf.webuntis.com"
		private const val FIXTURE_SCHOOL = "schuldorf"
		private const val FIXTURE_USER = "test.user"
		private const val FIXTURE_SCHOOL_NUMBER = "4738900"
		// A stored app-specific shared secret is not the account password.
		private const val FIXTURE_APP_SECRET = "app-shared-key-5f3c9d2e7b14"
		private const val FIXTURE_ACCOUNT_PASSWORD = "dummy-account-password-not-for-qr"
	}

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

	@Test
	fun buildUri_officialProfile_hasOnePayloadWithOnlyOfficialParameters() {
		val uri = officialUri()

		assertEquals(1, uri.count { it == '?' })
		assertEquals(4, uri.count { it == '&' })
		assertEquals(-1, uri.indexOf("untis://", 1))
		assertEquals(-1, uri.indexOf('#'))
		assertEquals(-1, uri.indexOf('\n'))
		assertEquals(
			listOf("url", "school", "user", "key", "schoolNumber"),
			queryNames(uri)
		)
	}

	@Test
	fun buildUri_blankCoreFields_areOmitted() {
		assertEquals(
			"untis://setschool",
			officialUri(url = "  ", school = "\t", user = "", key = " ", schoolNumber = "\n")
		)
		assertEquals(
			listOf("school", "user", "key"),
			queryNames(ProfileQrExport.buildUri(school = "school", user = "user", key = FIXTURE_APP_SECRET))
		)
	}

	@Test
	fun buildUri_keyIsAppSecret_accountPasswordIsNeverEncoded() {
		val uri = officialUri(key = FIXTURE_APP_SECRET)

		assertFalse(uri.contains(FIXTURE_ACCOUNT_PASSWORD, ignoreCase = true))
		assertFalse(uri.contains("password", ignoreCase = true))
		assertFalse(uri.contains("pass", ignoreCase = true))
		assertFalse(queryNames(uri).any { it.equals("password", ignoreCase = true) })
		assertTrue(uri.contains("key=$FIXTURE_APP_SECRET"))
		assertEquals(FIXTURE_APP_SECRET, requireNotNull(ProfileQrExport.parseUri(uri)).key)
	}

	@Test
	fun loginData_loadFromStoredUser_keepsAppSecretAndNoPassword() {
		val loginData = LoginDataInputViewModel.LoginData()

		loginData.loadFromUser(storedUser())

		assertEquals(FIXTURE_APP_SECRET, loginData.storedPassword)
		assertNull(loginData.password.value)
		assertEquals(FIXTURE_USER, loginData.username.value)
		assertEquals(FIXTURE_SCHOOL, loginData.schoolName.value)
	}

	@Test
	fun parseUri_officialPayload_usesStoredKeyAsAppSecretDirectly() {
		val payload = requireNotNull(ProfileQrExport.parseUri(officialUri()))

		// The official format implies skipAppSecret, so login reuses `key` as the
		// app shared secret instead of exchanging a password.
		assertTrue(ProfileQrExport.shouldSkipAppSecret(payload))
		assertEquals(FIXTURE_APP_SECRET, payload.key)
		assertNull(payload.skipAppSecret)
		assertNull(payload.apiUrl)
	}

	@Test
	fun parseUri_structurallyMalformedPayloads_returnNullWithoutThrowing() {
		val invalid = listOf<String?>(
			null,
			"",
			"   \n\t",
			FIXTURE_URL,
			"untis:setschool?school=s",
			"https://setschool?school=s&user=u&key=k",
			"untis://other?school=s&user=u&key=k",
			"untis://?school=s&user=u&key=k",
			"untis://setschool/?school=s&user=u&key=k",
			"untis://setschools?school=s&user=u&key=k",
		)

		invalid.forEach { raw ->
			val result = runCatching { ProfileQrExport.parseUri(raw) }
			assertTrue("parser threw for $raw", result.isSuccess)
			assertNull("expected rejection for $raw", result.getOrNull())
		}
	}

	@Test
	fun parseUri_truncatedAndTamperedKeys_doNotThrowOrRestoreOriginalSecret() {
		val base = "untis://setschool?url=$FIXTURE_URL&school=$FIXTURE_SCHOOL&user=$FIXTURE_USER"
		val damaged = listOf(
			base,
			"$base&key=",
			"$base&key=%",
			"$base&key=%ZZ",
			"$base&key=%E2%82",
			"$base&key=short",
			"$base&key=$FIXTURE_APP_SECRET&key=tampered",
		)

		damaged.forEach { raw ->
			val result = runCatching { ProfileQrExport.parseUri(raw) }
			assertTrue("parser threw for $raw", result.isSuccess)
			assertTrue(
				"damaged payload still carried the stored key: $raw",
				result.getOrNull()?.key != FIXTURE_APP_SECRET
			)
		}
	}

	@Test
	fun parseUri_malformedQuerySeparators_doNotThrowAndKeepValidFields() {
		val query = "url=$FIXTURE_URL&school=$FIXTURE_SCHOOL&user=$FIXTURE_USER&key=$FIXTURE_APP_SECRET"
		val damaged = listOf(
			"untis://setschool?$query&",
			"untis://setschool?$query&&extra=1",
			"untis://setschool?=ignored&$query",
			"untis://setschool?$query#fragment",
			"untis://setschool?$query&key",
		)

		damaged.forEach { raw ->
			val result = runCatching { ProfileQrExport.parseUri(raw) }
			assertTrue("parser threw for $raw", result.isSuccess)
			assertEquals("valid fields lost for $raw", FIXTURE_USER, result.getOrNull()?.user)
		}
	}

	@Test
	fun exportImportExport_officialProfile_isEquivalent() {
		val exported = officialUri()
		val imported = requireNotNull(ProfileQrExport.parseUri(exported))
		val reExported = reExport(imported)
		val reImported = requireNotNull(ProfileQrExport.parseUri(reExported))

		assertEquals(exported, reExported)
		assertEquals(imported, reImported)
		assertEquals(FIXTURE_APP_SECRET, reImported.key)
		assertEquals(
			ProfileQrExport.shouldSkipAppSecret(imported),
			ProfileQrExport.shouldSkipAppSecret(reImported)
		)
	}

	@Test
	fun exportImportExport_profileWithExtras_isEquivalent() {
		val exported = ProfileQrExport.buildUri(
			url = "https://plus.example/WebUntis?tenant=42",
			school = "sch&ool",
			user = "u ser",
			key = "a&b=c ü",
			schoolNumber = "123",
			anonymous = true,
			apiUrl = "https://plus.example/api?mode=test",
		)
		val imported = requireNotNull(ProfileQrExport.parseUri(exported))
		val reExported = reExport(imported)

		assertEquals(exported, reExported)
		assertEquals(imported, requireNotNull(ProfileQrExport.parseUri(reExported)))
		assertEquals(
			listOf("url", "school", "user", "key", "schoolNumber", "anonymous", "apiUrl"),
			queryNames(exported)
		)
	}

	@Test
	fun parseUri_whitespaceAndUnicodeNames_roundTripExactly() {
		val school = " São Paulo — 测试 "
		val user = "  Ana  Müller  "
		val url = "https://schule.example/WebUntis?name=Müller"
		val imported = requireNotNull(
			ProfileQrExport.parseUri(officialUri(url = url, school = school, user = user))
		)

		assertEquals(school, imported.school)
		assertEquals(user, imported.user)
		assertEquals(url, imported.url)
		assertEquals(imported, requireNotNull(ProfileQrExport.parseUri(reExport(imported))))
	}

	@Test
	fun parseUri_unicodeAppSecret_survivesRoundTrip() {
		val secret = "ключ-🔑-café"
		val exported = officialUri(key = secret)
		val imported = requireNotNull(ProfileQrExport.parseUri(exported))

		assertEquals(secret, imported.key)
		assertEquals(exported, reExport(imported))
	}

	@Test
	fun normalizeApiEndpoint_invalidLocalInputsAreRejectedWithoutRequests() {
		listOf("", "   ", "\n\t", "http://", "https://", "http://:8080", "https://?x=1", "https://#f")
			.forEach { assertNull("endpoint accepted: $it", normalizeApiEndpoint(it)) }
		assertEquals("https" to FIXTURE_URL, normalizeApiEndpoint(FIXTURE_URL))

		val blankFields = requireNotNull(
			ProfileQrExport.parseUri("untis://setschool?url=%20&school=%20&user=%20&key=%20")
		)
		assertNull(blankFields.url)
		assertNull(blankFields.school)
		assertNull(blankFields.user)
		assertNull(blankFields.key)
	}

	@Test
	fun importTarget_qrCarriesNoProfileId_andRouteChoosesNewOrExplicitProfile() {
		val uri = officialUri()

		assertFalse(queryNames(uri).any { it.contains("id", ignoreCase = true) })

		// Intended behavior: scanning a QR from login has no target profile id, so a
		// new profile is created. A different existing profile is only replaced when
		// the caller explicitly navigates with that profile's id.
		assertEquals(-1L, AppRoutes.LoginDataInput(autoLoginData = uri).userId)
		assertEquals(42L, AppRoutes.LoginDataInput(userId = 42L, autoLoginData = uri).userId)
	}

	private fun officialUri(
		url: String = FIXTURE_URL,
		school: String = FIXTURE_SCHOOL,
		user: String = FIXTURE_USER,
		key: String = FIXTURE_APP_SECRET,
		schoolNumber: String = FIXTURE_SCHOOL_NUMBER,
	): String = ProfileQrExport.buildUri(
		url = url,
		school = school,
		user = user,
		key = key,
		schoolNumber = schoolNumber,
	)

	private fun queryNames(uri: String): List<String> =
		uri.substringAfter('?', missingDelimiterValue = "")
			.split('&')
			.filter { it.isNotEmpty() }
			.map { it.substringBefore('=') }

	/** Mirrors the export dialog, which never emits the inferred skip flag. */
	private fun reExport(payload: ProfileQrExport.SetSchoolPayload): String =
		ProfileQrExport.buildUri(
			url = payload.url,
			school = payload.school,
			user = payload.user,
			key = payload.key,
			schoolNumber = payload.schoolNumber,
			anonymous = payload.anonymous,
			apiUrl = payload.apiUrl,
		)

	private fun storedUser(
		school: String = FIXTURE_SCHOOL,
		user: String = FIXTURE_USER,
		key: String = FIXTURE_APP_SECRET,
	): User = User(
		id = 7L,
		profileName = "Local Fixture",
		apiHost = FIXTURE_URL,
		schoolInfo = SchoolInfo(
			server = FIXTURE_URL,
			useMobileServiceUrlAndroid = true,
			useMobileServiceUrlIos = true,
			address = "",
			displayName = school,
			loginName = school,
			schoolId = FIXTURE_SCHOOL_NUMBER.toLong(),
			serverUrl = "https://$FIXTURE_URL",
			mobileServiceUrl = null,
		),
		user = user,
		key = key,
		anonymous = false,
		useUntisPlusApi = false,
		customApiHost = null,
		timeGrid = TimeGrid(emptyList()),
		masterDataTimestamp = 0L,
		userData = UserData(
			elemId = 1L,
			elemType = null,
			displayName = user,
			schoolName = school,
			departmentId = 0L,
		),
		settings = null,
	)
}
