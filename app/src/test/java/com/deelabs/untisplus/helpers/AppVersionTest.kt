package com.deelabs.untisplus.helpers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppVersionTest {
	@Test
	fun `stable release is newer than current`() {
		assertTrue(AppVersion.isNewer("v1.3.0", "v1.2.0"))
		assertTrue(AppVersion.isNewer("v1.2.1", "v1.2.0"))
	}

	@Test
	fun `older or equal release is not newer`() {
		assertFalse(AppVersion.isNewer("v1.1.9", "v1.2.0"))
		assertFalse(AppVersion.isNewer("v1.2.0", "v1.2.0"))
	}

	@Test
	fun `stable release outranks its beta`() {
		assertTrue(AppVersion.isNewer("v1.2.0", "v1.2.0-beta.2"))
		assertFalse(AppVersion.isNewer("v1.2.0-beta.2", "v1.2.0"))
		assertTrue(AppVersion.isNewer("v1.2.0-beta.3", "v1.2.0-beta.2"))
	}

	@Test
	fun `unparseable tags are ignored`() {
		assertNull(AppVersion.parse("nightly"))
		assertFalse(AppVersion.isNewer("nightly", "v1.2.0"))
	}
}
