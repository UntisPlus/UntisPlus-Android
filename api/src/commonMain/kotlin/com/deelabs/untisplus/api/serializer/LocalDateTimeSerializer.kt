package com.deelabs.untisplus.api.serializer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("kotlinx.datetime.LocalDateTime", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: LocalDateTime) {
		val string = value.date.toString() +
			"T" +
			value.hour.toString().padStart(2, '0') + ":" +
			value.minute.toString().padStart(2, '0') +
			"Z"
		encoder.encodeString(string)
	}

	override fun deserialize(decoder: Decoder): LocalDateTime {
		val string = decoder.decodeString()
		return try {
			val parts = string.removeSuffix("Z").split("T")
			LocalDateTime(LocalDate.parse(parts[0]), LocalTime.parse(parts[1]))
		} catch (e: Exception) {
			LocalDateTime(1970, 1, 1, 0, 0)
		}
	}
}
