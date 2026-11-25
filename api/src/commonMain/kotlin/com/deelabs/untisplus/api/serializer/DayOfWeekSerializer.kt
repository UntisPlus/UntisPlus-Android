package com.deelabs.untisplus.api.serializer

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DayOfWeekSerializer : KSerializer<DayOfWeek> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("kotlinx.datetime.DayOfWeek", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: DayOfWeek) {
		encoder.encodeString(value.name.take(3))
	}

	override fun deserialize(decoder: Decoder): DayOfWeek {
		val shortName = decoder.decodeString().uppercase()
		return DayOfWeek.entries.first { it.name.startsWith(shortName) }
	}
}
