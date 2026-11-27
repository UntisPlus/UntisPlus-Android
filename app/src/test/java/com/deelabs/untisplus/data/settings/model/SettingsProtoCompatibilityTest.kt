package com.deelabs.untisplus.data.settings.model

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class SettingsProtoCompatibilityTest {
	@Test
	fun `every proto message has unique field numbers and names`() {
		val byMessage = schema.fields.groupBy { it.message }
		assertTrue(byMessage.keys.containsAll(listOf("UserSettings", "GlobalSettings", "Settings", "TimetableElement")))

		for ((messageName, fields) in byMessage) {
			val numbers = fields.map { it.number }
			val names = fields.map { it.name }
			assertEquals(numbers.size, numbers.toSet().size, "$messageName has duplicate field numbers: $numbers")
			assertEquals(names.size, names.toSet().size, "$messageName has duplicate field names: $names")
		}
	}

	@Test
	fun `new settings fields keep their assigned numbers and names`() {
		val userFields = schema.fields.filter { it.message == "UserSettings" }
		val expected = mapOf(
			"infocenterMessageNotifications" to 68,
			"infocenterMessageNotificationsSeen" to 69,
			"infocenterMessageNotificationsInitialized" to 70,
			"eventRemindersEnabled" to 71,
		)

		for ((name, number) in expected) {
			assertEquals(number, userFields.singleOrNull { it.name == name }?.number, "Unexpected number for $name")
		}
		for (number in expected.values) {
			assertEquals(1, userFields.count { it.number == number }, "Field number $number was reused")
		}
	}

	@Test
	fun `generated field constants match the proto schema`() {
		val messages = listOf(
			"UserSettings" to UserSettings::class.java,
			"GlobalSettings" to GlobalSettings::class.java,
			"Settings" to Settings::class.java,
			"TimetableElement" to TimetableElement::class.java,
		)

		for ((messageName, messageClass) in messages) {
			val expected = schema.fields
				.filter { it.message == messageName }
				.associate { it.name.lowercase() to it.number }
			assertEquals(expected, generatedFieldNumbers(messageClass), "$messageName generated field numbers")
		}
	}

	@Test
	fun `defaults of all fields match proto declarations`() {
		val messages = listOf("UserSettings", "GlobalSettings", "Settings", "TimetableElement")

		for (messageName in messages) {
			val messageClass = Class.forName("${schema.javaPackage}.$messageName")
			val defaultInstance = messageClass.getMethod("getDefaultInstance").invoke(null)
			val builder = messageClass.getMethod("newBuilder").invoke(null)
			val builtFromBuilder = builder.javaClass.getMethod("build").invoke(builder)

			for (field in schema.fields.filter { it.message == messageName }) {
				val expected = expectedDefault(field)
				val getter = getter(messageClass, field)
				for (instance in listOf(defaultInstance, builtFromBuilder)) {
					assertDefaultEquals(expected, getter.invoke(instance), "$messageName.${field.name} default")
				}
			}
		}
	}

	@Test
	fun `new notification opt ins default off`() {
		val defaults = UserSettings.getDefaultInstance()

		assertFalse(defaults.infocenterMessageNotifications)
		assertTrue(defaults.infocenterMessageNotificationsSeenMap.isEmpty())
		assertFalse(defaults.infocenterMessageNotificationsInitialized)
		assertFalse(defaults.eventRemindersEnabled)

		val built = UserSettings.newBuilder().build()
		assertFalse(built.infocenterMessageNotifications)
		assertTrue(built.infocenterMessageNotificationsSeenMap.isEmpty())
		assertFalse(built.infocenterMessageNotificationsInitialized)
		assertFalse(built.eventRemindersEnabled)
	}

	@Test
	fun `existing notification opt ins default off`() {
		val defaults = UserSettings.getDefaultInstance()

		assertFalse(defaults.notificationsEnable)
		assertFalse(defaults.notificationsInMultiple)
		assertFalse(defaults.notificationsBeforeFirst)
		assertFalse(defaults.timetableChangeNotifications)
		assertFalse(defaults.automuteAllowPriority)
		assertFalse(defaults.timetableChangeWifiOnly)
	}

	@Test
	fun `explicit true defaults are preserved`() {
		val defaults = UserSettings.getDefaultInstance()

		assertTrue(defaults.automuteCancelledLessons)
		assertTrue(defaults.timetableSubstitutionsIrregular)
		assertTrue(defaults.timetableCenteredLessonInfo)
		assertTrue(defaults.timetableBoldLessonName)
		assertTrue(defaults.timetableZoomEnabled)
		assertTrue(defaults.connectivityRefreshInBackground)
		assertTrue(defaults.timetableShowImportedCalendars)
		assertTrue(GlobalSettings.getDefaultInstance().errorReportingEnable)
		assertTrue(GlobalSettings.getDefaultInstance().errorReportingEnableBreadcrumbs)
	}

	@Test
	fun `old user settings bytes deserialize with new fields defaulted`() {
		val legacy = UserSettings.parseFrom(legacyUserSettingsBytes())

		assertTrue(legacy.exitConfirmation)
		assertEquals("old-range", legacy.timetableRange)
		assertEquals(9, legacy.timetableItemPadding)
		assertEquals(NotificationVisibility.LONG, legacy.notificationsVisibilitySubjects)
		assertEquals("old-proxy-host", legacy.proxyHost)
		assertTrue(legacy.timetableChangeNotifications)

		assertFalse(legacy.hasInfocenterMessageNotifications())
		assertFalse(legacy.infocenterMessageNotifications)
		assertTrue(legacy.infocenterMessageNotificationsSeenMap.isEmpty())
		assertFalse(legacy.hasInfocenterMessageNotificationsInitialized())
		assertFalse(legacy.infocenterMessageNotificationsInitialized)
		assertFalse(legacy.hasEventRemindersEnabled())
		assertFalse(legacy.eventRemindersEnabled)
	}

	@Test
	fun `old top level settings bytes deserialize with new fields defaulted`() {
		val parsed = Settings.parseFrom(legacySettingsBytes(700L))

		assertEquals(700L, parsed.activeUser)
		assertEquals(GlobalSettings.getDefaultInstance(), parsed.globalSettings)

		val userSettings = parsed.userSettingsMap.getValue(700L)
		assertTrue(userSettings.exitConfirmation)
		assertEquals("old-proxy-host", userSettings.proxyHost)
		assertTrue(userSettings.timetableChangeNotifications)
		assertFalse(userSettings.infocenterMessageNotifications)
		assertTrue(userSettings.infocenterMessageNotificationsSeenMap.isEmpty())
		assertFalse(userSettings.infocenterMessageNotificationsInitialized)
		assertFalse(userSettings.eventRemindersEnabled)
	}

	@Test
	fun `all four new fields round trip without loss`() {
		val original = UserSettings.newBuilder()
			.setInfocenterMessageNotifications(true)
			.putInfocenterMessageNotificationsSeen("message:7", "sig-a")
			.putInfocenterMessageNotificationsSeen("motd:9", "sig-b")
			.setInfocenterMessageNotificationsInitialized(true)
			.setEventRemindersEnabled(true)
			.build()

		val parsed = UserSettings.parseFrom(original.toByteArray())

		assertEquals(original, parsed)
		assertTrue(parsed.infocenterMessageNotifications)
		assertEquals(
			mapOf("message:7" to "sig-a", "motd:9" to "sig-b"),
			parsed.infocenterMessageNotificationsSeenMap,
		)
		assertTrue(parsed.infocenterMessageNotificationsInitialized)
		assertTrue(parsed.eventRemindersEnabled)
	}

	@Test
	fun `new fields round trip independently for multiple profiles`() {
		val first = UserSettings.newBuilder()
			.setInfocenterMessageNotifications(true)
			.putInfocenterMessageNotificationsSeen("message:1", "sig-first")
			.setEventRemindersEnabled(false)
			.build()
		val second = UserSettings.newBuilder()
			.setInfocenterMessageNotifications(false)
			.setInfocenterMessageNotificationsInitialized(true)
			.setEventRemindersEnabled(true)
			.build()
		val original = Settings.newBuilder()
			.setActiveUser(10L)
			.putUserSettings(10L, first)
			.putUserSettings(20L, second)
			.build()

		val parsed = Settings.parseFrom(original.toByteArray())

		assertEquals(original, parsed)
		assertEquals(first, parsed.userSettingsMap.getValue(10L))
		assertEquals(second, parsed.userSettingsMap.getValue(20L))
		assertTrue(parsed.userSettingsMap.getValue(10L).infocenterMessageNotifications)
		assertFalse(parsed.userSettingsMap.getValue(10L).eventRemindersEnabled)
		assertFalse(parsed.userSettingsMap.getValue(20L).infocenterMessageNotifications)
		assertTrue(parsed.userSettingsMap.getValue(20L).infocenterMessageNotificationsInitialized)
		assertTrue(parsed.userSettingsMap.getValue(20L).eventRemindersEnabled)
	}

	@Test
	fun `future user settings fields survive serialization`() {
		val unknownField = varintFieldBytes(1000, 987654321)
		val bytes = legacyUserSettingsBytes() + unknownField

		val parsed = UserSettings.parseFrom(bytes)
		assertTrue(parsed.toByteArray().containsSequence(unknownField), "Unknown field was dropped on parse")

		val modified = parsed.toBuilder().setInfocenterMessageNotifications(true).build().toByteArray()
		assertTrue(modified.containsSequence(unknownField), "Unknown field was dropped after modification")

		val reparsed = UserSettings.parseFrom(modified)
		assertTrue(reparsed.infocenterMessageNotifications)
		assertEquals("old-proxy-host", reparsed.proxyHost)
	}

	@Test
	fun `future top level settings fields survive serialization`() {
		val unknownField = varintFieldBytes(1000, 13572468)
		val bytes = legacySettingsBytes(700L) + unknownField

		val parsed = Settings.parseFrom(bytes)
		assertTrue(parsed.toByteArray().containsSequence(unknownField), "Unknown field was dropped on parse")

		val modified = parsed.toBuilder().setActiveUser(701L).build().toByteArray()
		assertTrue(modified.containsSequence(unknownField), "Unknown field was dropped after modification")
		assertEquals(701L, Settings.parseFrom(modified).activeUser)
	}

	@Test
	fun `toggling message notifications leaves other opt ins unchanged`() {
		val before = fullyOptedInUserSettings()

		val after = before.toBuilder().setInfocenterMessageNotifications(false).build()

		assertFalse(after.infocenterMessageNotifications)
		assertEquals(before.infocenterMessageNotificationsSeenMap, after.infocenterMessageNotificationsSeenMap)
		assertEquals(before.infocenterMessageNotificationsInitialized, after.infocenterMessageNotificationsInitialized)
		assertEquals(before.eventRemindersEnabled, after.eventRemindersEnabled)
		assertEquals(before.notificationsEnable, after.notificationsEnable)
		assertEquals(before.notificationsInMultiple, after.notificationsInMultiple)
		assertEquals(before.notificationsBeforeFirst, after.notificationsBeforeFirst)
		assertEquals(before.timetableChangeNotifications, after.timetableChangeNotifications)
	}

	@Test
	fun `toggling message notifications initialized leaves other opt ins unchanged`() {
		val before = fullyOptedInUserSettings()

		val after = before.toBuilder().setInfocenterMessageNotificationsInitialized(false).build()

		assertFalse(after.infocenterMessageNotificationsInitialized)
		assertEquals(before.infocenterMessageNotifications, after.infocenterMessageNotifications)
		assertEquals(before.infocenterMessageNotificationsSeenMap, after.infocenterMessageNotificationsSeenMap)
		assertEquals(before.eventRemindersEnabled, after.eventRemindersEnabled)
		assertEquals(before.notificationsEnable, after.notificationsEnable)
		assertEquals(before.timetableChangeNotifications, after.timetableChangeNotifications)
	}

	@Test
	fun `toggling event reminders leaves other opt ins unchanged`() {
		val before = fullyOptedInUserSettings()

		val after = before.toBuilder().setEventRemindersEnabled(false).build()

		assertFalse(after.eventRemindersEnabled)
		assertEquals(before.infocenterMessageNotifications, after.infocenterMessageNotifications)
		assertEquals(before.infocenterMessageNotificationsSeenMap, after.infocenterMessageNotificationsSeenMap)
		assertEquals(before.infocenterMessageNotificationsInitialized, after.infocenterMessageNotificationsInitialized)
		assertEquals(before.notificationsEnable, after.notificationsEnable)
		assertEquals(before.timetableChangeNotifications, after.timetableChangeNotifications)
	}

	@Test
	fun `turning on one opt in leaves the other new opt ins off`() {
		val after = UserSettings.getDefaultInstance().toBuilder().setEventRemindersEnabled(true).build()

		assertTrue(after.eventRemindersEnabled)
		assertFalse(after.infocenterMessageNotifications)
		assertTrue(after.infocenterMessageNotificationsSeenMap.isEmpty())
		assertFalse(after.infocenterMessageNotificationsInitialized)
	}

	@Test
	fun `updating one profile leaves other profile opt ins unchanged`() {
		val optedIn = UserSettings.newBuilder()
			.setInfocenterMessageNotifications(true)
			.setInfocenterMessageNotificationsInitialized(true)
			.setEventRemindersEnabled(true)
			.build()
		val settings = Settings.newBuilder()
			.putUserSettings(1L, optedIn)
			.putUserSettings(2L, optedIn)
			.build()

		val updatedFirst = settings.userSettingsMap.getValue(1L).toBuilder()
			.setInfocenterMessageNotifications(false)
			.build()
		val updated = settings.toBuilder().putUserSettings(1L, updatedFirst).build()

		assertFalse(updated.userSettingsMap.getValue(1L).infocenterMessageNotifications)
		assertTrue(updated.userSettingsMap.getValue(2L).infocenterMessageNotifications)
		assertTrue(updated.userSettingsMap.getValue(1L).eventRemindersEnabled)
		assertTrue(updated.userSettingsMap.getValue(2L).eventRemindersEnabled)
	}

	private companion object {
		val integer32Types = setOf("int32", "uint32", "sint32", "fixed32", "sfixed32")
		val integer64Types = setOf("int64", "uint64", "sint64", "fixed64", "sfixed64")
		val fieldPattern = Regex(
			"""^(?:repeated\s+|optional\s+)?(map<[^>]+>|[\w.]+)\s+(\w+)\s*=\s*(\d+)\s*(?:\[([^\]]*)\])?\s*;$"""
		)
		val enumValuePattern = Regex("""^(\w+)\s*=\s*(-?\d+)\s*;$""")
		val defaultPattern = Regex("""default\s*=\s*([^,\]]+)""")

		val schema: ProtoSchema by lazy { parseProto() }

		fun fullyOptedInUserSettings(): UserSettings = UserSettings.newBuilder()
			.setInfocenterMessageNotifications(true)
			.putInfocenterMessageNotificationsSeen("message:1", "sig-a")
			.setInfocenterMessageNotificationsInitialized(true)
			.setEventRemindersEnabled(true)
			.setNotificationsEnable(true)
			.setNotificationsInMultiple(true)
			.setNotificationsBeforeFirst(true)
			.setTimetableChangeNotifications(true)
			.build()

		fun legacyUserSettingsBytes(): ByteArray {
			val bytes = ByteArrayOutputStream()
			val output = CodedOutputStream.newInstance(bytes)
			output.writeBool(1, true)
			output.writeString(30, "old-range")
			output.writeUInt32(33, 9)
			output.writeEnum(43, NotificationVisibility.LONG_VALUE)
			output.writeString(48, "old-proxy-host")
			output.writeBool(60, true)
			output.writeString(63, "PERSONAL")
			output.flush()
			return bytes.toByteArray()
		}

		fun legacySettingsBytes(userId: Long): ByteArray {
			val entryBytes = ByteArrayOutputStream()
			val entryOutput = CodedOutputStream.newInstance(entryBytes)
			entryOutput.writeUInt64(1, userId)
			entryOutput.writeBytes(2, ByteString.copyFrom(UserSettings.parseFrom(legacyUserSettingsBytes()).toByteArray()))
			entryOutput.flush()

			val settingsBytes = ByteArrayOutputStream()
			val output = CodedOutputStream.newInstance(settingsBytes)
			output.writeUInt64(1, userId)
			output.writeBytes(2, ByteString.copyFrom(entryBytes.toByteArray()))
			output.flush()
			return settingsBytes.toByteArray()
		}

		fun varintFieldBytes(fieldNumber: Int, value: Int): ByteArray {
			val bytes = ByteArrayOutputStream()
			val output = CodedOutputStream.newInstance(bytes)
			output.writeUInt32(fieldNumber, value)
			output.flush()
			return bytes.toByteArray()
		}

		fun ByteArray.containsSequence(needle: ByteArray): Boolean {
			if (needle.isEmpty()) return true
			if (needle.size > size) return false
			return (0..size - needle.size).any { start ->
				needle.indices.all { offset -> this[start + offset] == needle[offset] }
			}
		}

		fun protoFile(): File {
			val candidates = listOf(
				File("src/main/proto/settings.proto"),
				File("app/src/main/proto/settings.proto"),
			)
			return candidates.firstOrNull { it.isFile }
				?: error("Could not find settings.proto in ${candidates.joinToString { it.absolutePath }}")
		}

		fun parseProto(): ProtoSchema {
			val text = protoFile().readText()
			val javaPackage = Regex("""option\s+java_package\s*=\s*"([^"]+)";""")
				.find(text)
				?.groupValues?.get(1)
				?: error("settings.proto has no java_package option")

			val fields = mutableListOf<ProtoField>()
			val enumZeroConstants = mutableMapOf<String, String>()
			var message: String? = null
			var enum: String? = null

			for (rawLine in text.lineSequence()) {
				val line = rawLine.substringBefore("//").trim()
				when {
					line.startsWith("message ") -> {
						message = line.removePrefix("message ").substringBefore("{").trim()
						enum = null
					}
					line.startsWith("enum ") -> {
						enum = line.removePrefix("enum ").substringBefore("{").trim()
						message = null
					}
					line == "}" -> {
						message = null
						enum = null
					}
					message != null -> {
						fieldPattern.matchEntire(line)?.let { match ->
							val type = match.groupValues[1]
							fields += ProtoField(
								message = message,
								type = type,
								name = match.groupValues[2],
								number = match.groupValues[3].toInt(),
								defaultLiteral = defaultPattern.find(match.groupValues[4])?.groupValues?.get(1)?.trim(),
								repeated = line.startsWith("repeated ") || type.startsWith("map<"),
								map = type.startsWith("map<"),
							)
						}
					}
					enum != null -> {
						enumValuePattern.matchEntire(line)?.let { match ->
							if (match.groupValues[2].toInt() == 0) enumZeroConstants[enum] = match.groupValues[1]
						}
					}
				}
			}

			return ProtoSchema(javaPackage, fields, enumZeroConstants)
		}

		fun generatedFieldNumbers(messageClass: Class<*>): Map<String, Int> =
			messageClass.declaredFields
				.filter { it.name.endsWith("_FIELD_NUMBER") && Modifier.isStatic(it.modifiers) }
				.associate { field ->
					field.name.removeSuffix("_FIELD_NUMBER").lowercase() to field.getInt(null)
				}

		fun getter(messageClass: Class<*>, field: ProtoField): Method {
			val capitalized = field.name.replaceFirstChar { it.uppercaseChar() }
			val name = when {
				field.map -> "get${capitalized}Map"
				field.repeated -> "get${capitalized}List"
				else -> "get$capitalized"
			}
			return messageClass.getMethod(name)
		}

		fun expectedDefault(field: ProtoField): Any {
			field.defaultLiteral?.let { return parseDefaultLiteral(it, field.type) }
			return when {
				field.map -> emptyMap<Any, Any>()
				field.repeated -> emptyList<Any>()
				field.type == "bool" -> false
				field.type == "float" -> 0f
				field.type == "double" -> 0.0
				field.type in integer32Types -> 0
				field.type in integer64Types -> 0L
				field.type == "string" -> ""
				field.type == "bytes" -> ByteString.EMPTY
				field.type in schema.enumZeroConstants ->
					enumConstant(field.type, schema.enumZeroConstants.getValue(field.type))
				else -> Class.forName("${schema.javaPackage}.${field.type}")
					.getMethod("getDefaultInstance")
					.invoke(null)
			}
		}

		fun parseDefaultLiteral(literal: String, type: String): Any = when {
			type == "bool" -> literal.toBooleanStrict()
			type == "float" -> literal.removeSuffix("f").removeSuffix("F").toFloat()
			type == "double" -> literal.removeSuffix("d").removeSuffix("D").toDouble()
			type in integer32Types -> parseLongLiteral(literal).toInt()
			type in integer64Types -> parseLongLiteral(literal)
			type == "string" -> literal.trim().removePrefix("\"").removeSuffix("\"")
			type in schema.enumZeroConstants -> enumConstant(type, literal.substringAfterLast('.'))
			else -> error("Unsupported default literal '$literal' for type $type")
		}

		fun parseLongLiteral(literal: String): Long =
			if (literal.startsWith("0x", ignoreCase = true)) java.lang.Long.decode(literal) else literal.toLong()

		fun enumConstant(type: String, name: String): Any {
			val enumClass = Class.forName("${schema.javaPackage}.$type")
			return enumClass.enumConstants!!.first { (it as Enum<*>).name == name }
		}

		fun assertDefaultEquals(expected: Any, actual: Any?, message: String) {
			when {
				expected is Float && actual is Float -> assertEquals(expected, actual, 0.00001f, message)
				expected is Double && actual is Double -> assertEquals(expected, actual, 0.0000001, message)
				else -> assertEquals(expected, actual, message)
			}
		}
	}
}

private data class ProtoField(
	val message: String,
	val type: String,
	val name: String,
	val number: Int,
	val defaultLiteral: String?,
	val repeated: Boolean,
	val map: Boolean,
)

private data class ProtoSchema(
	val javaPackage: String,
	val fields: List<ProtoField>,
	val enumZeroConstants: Map<String, String>,
)
