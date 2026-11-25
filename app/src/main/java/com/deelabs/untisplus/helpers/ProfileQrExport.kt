package com.deelabs.untisplus.helpers

/**
 * Builds the `untis://setschool` payload understood by
 * `LoginDataInputViewModel.loadFromData()`.
 *
 * The value passed as [key] is the stored app-specific access key, not the
 * account password. The importer must therefore receive `skipAppSecret=true`
 * so it does not try to exchange the key like a password.
 */
object ProfileQrExport {
	private const val BASE_URI = "untis://setschool"
	private const val HEX_DIGITS = "0123456789ABCDEF"

	fun buildUri(
		school: String?,
		user: String?,
		key: String?,
		anonymous: Boolean = false,
		apiUrl: String? = null,
		skipAppSecret: Boolean = false,
	): String {
		val params = mutableListOf<Pair<String, String>>()

		school.takeIf { it?.isNotBlank() == true }?.let { params += "school" to it }
		user.takeIf { it?.isNotBlank() == true }?.let { params += "user" to it }
		key.takeIf { it?.isNotBlank() == true }?.let { params += "key" to it }

		if (anonymous) params += "anonymous" to "true"
		apiUrl.takeIf { it?.isNotBlank() == true }?.let { params += "apiUrl" to it }
		if (skipAppSecret && key?.isNotBlank() == true) params += "skipAppSecret" to "true"

		if (params.isEmpty()) return BASE_URI
		return params.joinToString("&", prefix = "$BASE_URI?") { (name, value) ->
			"$name=${encodeQueryComponent(value)}"
		}
	}

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
}
