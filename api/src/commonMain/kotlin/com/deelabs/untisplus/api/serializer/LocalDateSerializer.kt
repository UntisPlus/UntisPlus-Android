package com.deelabs.untisplus.api.serializer

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateSerializer : KSerializer<LocalDate> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("kotlinx.datetime.LocalDate", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: LocalDate) {
		encoder.encodeString(value.toString())
	}

	override fun deserialize(decoder: Decoder): LocalDate {
		val string = decoder.decodeString()
		return try {
			LocalDate.parse(string)
		} catch (e: IllegalArgumentException) {
			Instant.fromEpochMilliseconds(0).toLocalDateTime(TimeZone.currentSystemDefault()).date
		}
	}
}
