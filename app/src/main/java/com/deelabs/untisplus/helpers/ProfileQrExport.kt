package com.deelabs.untisplus.helpers

/**
 * Builds and parses `untis://setschool` payloads.
 *
 * Standard profiles use the official WebUntis dashboard parameter order:
 * `url`, `school`, `user`, `key`, `schoolNumber`. UntisPlus-only extras
 * (`anonymous`, `apiUrl`, `skipAppSecret`) are appended after those five and
 * may not be accepted by the official Untis app.
 */
object ProfileQrExport {
	private const val BASE_URI = "untis://setschool"
	private const val HEX_DIGITS = "0123456789ABCDEF"

	data class SetSchoolPayload(
		val url: String?,
		val school: String?,
		val user: String?,
		val key: String?,
		val schoolNumber: String?,
		val anonymous: Boolean,
		val apiUrl: String?,
		val skipAppSecret: Boolean?,
	)

	fun buildUri(
		url: String? = null,
		school: String? = null,
		user: String? = null,
		key: String? = null,
		schoolNumber: String? = null,
		anonymous: Boolean = false,
		apiUrl: String? = null,
		skipAppSecret: Boolean = false,
	): String {
		val params = mutableListOf<Pair<String, String>>()

		// Keep this order identical to the official dashboard QR.
		url.takeIf { it?.isNotBlank() == true }?.let { params += "url" to it }
		school.takeIf { it?.isNotBlank() == true }?.let { params += "school" to it }
		user.takeIf { it?.isNotBlank() == true }?.let { params += "user" to it }
		key.takeIf { it?.isNotBlank() == true }?.let { params += "key" to it }
		schoolNumber.takeIf { it?.isNotBlank() == true }?.let { params += "schoolNumber" to it }

		// UntisPlus-only extras. The official app may reject codes containing these.
		if (anonymous) params += "anonymous" to "true"
		apiUrl.takeIf { it?.isNotBlank() == true }?.let { params += "apiUrl" to it }
		if (skipAppSecret && key?.isNotBlank() == true) params += "skipAppSecret" to "true"

		if (params.isEmpty()) return BASE_URI
		return params.joinToString("&", prefix = "$BASE_URI?") { (name, value) ->
			"$name=${encodeQueryComponent(value)}"
		}
	}

	fun parseUri(value: String?): SetSchoolPayload? {
		if (value.isNullOrBlank()) return null
		val trimmed = value.trim()
		val schemeEnd = trimmed.indexOf("://")
		if (schemeEnd < 0) return null
		if (!trimmed.substring(0, schemeEnd).equals("untis", ignoreCase = true)) return null

		val authorityAndQuery = trimmed.substring(schemeEnd + 3).substringBefore('#')
		val authority = authorityAndQuery.substringBefore('?')
		if (!authority.equals("setschool", ignoreCase = true)) return null

		val query = if ('?' in authorityAndQuery) authorityAndQuery.substringAfter('?') else ""
		val params = parseQuery(query)
		return SetSchoolPayload(
			url = params["url"]?.takeIf { it.isNotBlank() },
			school = params["school"]?.takeIf { it.isNotBlank() },
			user = params["user"]?.takeIf { it.isNotBlank() },
			key = params["key"]?.takeIf { it.isNotBlank() },
			schoolNumber = params["schoolNumber"]?.takeIf { it.isNotBlank() },
			anonymous = params["anonymous"]?.equals("true", ignoreCase = true) == true,
			apiUrl = params["apiUrl"]?.takeIf { it.isNotBlank() },
			skipAppSecret = params["skipAppSecret"]?.let { it.equals("true", ignoreCase = true) },
		)
	}

	/**
	 * Official dashboard codes carry the app-specific secret as `key` but omit
	 * `skipAppSecret`. Infer it from the presence of the official `url` param;
	 * an explicit flag (including `false`) always wins for legacy payloads.
	 */
	fun shouldSkipAppSecret(payload: SetSchoolPayload): Boolean =
		payload.skipAppSecret ?: (payload.url != null)

	/** RFC 3986 query-component encoding; Android's Uri.getQueryParameter decodes it. */
	private fun encodeQueryComponent(value: String): String {
		val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
		val result = StringBuilder()
		for (byte in value.encodeToByteArray()) {
			val code = byte.toInt() and 0xFF
			val char = code.toChar()
			if (char in allowed) {
				result.append(char)
			} else {
				result.append('%')
				result.append(HEX_DIGITS[code shr 4])
				result.append(HEX_DIGITS[code and 0xF])
			}
		}
		return result.toString()
	}

	private fun parseQuery(query: String): Map<String, String> {
		if (query.isEmpty()) return emptyMap()
		val result = linkedMapOf<String, String>()
		for (pair in query.split('&')) {
			if (pair.isEmpty()) continue
			val parts = pair.split('=', limit = 2)
			val name = decodeQueryComponent(parts[0])
			val value = if (parts.size == 2) decodeQueryComponent(parts[1]) else ""
			result[name] = value
		}
		return result
	}

	/** Decodes `%XX` bytes as UTF-8; a literal `+` stays a `+`. */
	private fun decodeQueryComponent(value: String): String {
		val bytes = mutableListOf<Byte>()
		var i = 0
		while (i < value.length) {
			val char = value[i]
			if (char == '%' && i + 2 < value.length) {
				val code = value.substring(i + 1, i + 3).toIntOrNull(16)
				if (code != null) {
					bytes.add(code.toByte())
					i += 3
					continue
				}
			}
			for (byte in char.toString().encodeToByteArray()) bytes.add(byte)
			i++
		}
		return bytes.toByteArray().toString(Charsets.UTF_8)
	}
}
