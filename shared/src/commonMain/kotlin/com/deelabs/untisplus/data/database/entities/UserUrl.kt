package com.deelabs.untisplus.data.database.entities

import com.deelabs.untisplus.api.model.untis.SchoolInfo

/**
 * Multiplatform (pure-Kotlin) API URL construction for [User]. Replaces the
 * previous `android.net.Uri`-based helpers so repositories and other shared
 * code can resolve endpoints without Android. The Android-only [User]
 * extensions in `UserAndroid.kt` delegate to these string builders.
 */

const val UNTISPLUS_API_HOST = "api-untis.deelabs.tech"

/** Beta deployment of the UntisPlus proxy; used by the login provider selector. */
const val UNTISPLUS_BETA_API_HOST = "beta-untis.deelabs.tech"

/**
 * Normalizes a user-provided API endpoint to a scheme/authority pair.
 * A missing scheme defaults to https; http is allowed for custom endpoints only.
 */
fun normalizeApiEndpoint(raw: String): Pair<String, String>? {
	val trimmed = raw.trim()
	if (trimmed.isEmpty()) return null
	val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
	val scheme = withScheme.substringBefore("://").ifBlank { "https" }
	val authority = authorityOf(withScheme.substringAfter("://"))
	if (authority.isBlank()) return null
	val host = authority.substringAfterLast('@').substringBefore(':')
	if (host.isBlank()) return null
	return scheme.lowercase() to authority
}

/** Whether [host] points at the beta UntisPlus proxy (with or without a scheme). */
fun isUntisPlusBetaApiHost(host: String?): Boolean =
	host?.let { normalizeApiEndpoint(it)?.second } == UNTISPLUS_BETA_API_HOST

fun buildApiUrlString(
	apiHost: String,
	schoolInfo: SchoolInfo? = null
): String {
	val raw = apiHost.ifBlank {
		if (schoolInfo?.useMobileServiceUrlAndroid == true && !schoolInfo.mobileServiceUrl.isNullOrBlank()) schoolInfo.mobileServiceUrl
		else schoolInfo?.serverUrl
	} ?: ""

	val scheme: String
	val authority: String
	if (raw.contains("://")) {
		scheme = raw.substringBefore("://").ifBlank { "https" }
		authority = authorityOf(raw.substringAfter("://"))
	} else {
		scheme = "https"
		authority = authorityOf(raw)
	}

	return "$scheme://$authority/WebUntis"
}

fun buildJsonRpcApiUrlString(apiUrl: String, schoolName: String): String {
	return "$apiUrl/jsonrpc_intern.do?school=${encodeQueryComponent(schoolName)}"
}

/**
 * Rewrites the scheme/authority of [url] based on the user's proxy/custom-host
 * configuration, mirroring [User.resolveApiEndpoint] on the Android side.
 */
fun resolveApiEndpoint(url: String, customApiHost: String?, useUntisPlusApi: Boolean): String {
	customApiHost?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
		normalizeApiEndpoint(raw)?.let { (scheme, authority) ->
			return replaceSchemeAuthority(url, scheme, authority)
		}
	}
	if (useUntisPlusApi) return replaceSchemeAuthority(url, "https", UNTISPLUS_API_HOST)
	return url
}

val User.apiUrlString: String
	get() = buildApiUrlString(apiHost, schoolInfo)

val User.jsonRpcApiUrlString: String
	get() {
		val schoolName = schoolInfo?.loginName ?: queryParameter(apiHost, "school") ?: ""
		return resolveApiEndpoint(
			buildJsonRpcApiUrlString(apiUrlString, schoolName),
			customApiHost,
			useUntisPlusApi
		)
	}

/** Origin (scheme + authority, no path, no query) of the resolved API host. */
val User.proxyBaseUrlString: String
	get() = originOfUrl(jsonRpcApiUrlString)

/** The `?school=` value for the proxy's own endpoints. See `docs/PROXY-API.md` §1. */
val User.proxySchoolNameValue: String?
	get() = (schoolInfo?.loginName ?: queryParameter(apiHost, "school"))
		?.takeIf { it.isNotBlank() }

val User.restApiUrlString: String
	get() = "$apiUrlString/api/rest"

val User.restApiAuthUrlString: String
	get() = "$apiUrlString/api/mobile/v2"

private fun authorityOf(rest: String): String =
	rest.substringBefore('/').substringBefore('?').substringBefore('#')

private fun originOfUrl(url: String): String {
	val scheme = url.substringBefore("://").ifBlank { "https" }
	val authority = authorityOf(url.substringAfter("://"))
	return "$scheme://$authority"
}

private fun replaceSchemeAuthority(url: String, scheme: String, authority: String): String {
	val rest = url.substringAfter("://")
	val remainder = rest.dropWhile { it != '/' && it != '?' && it != '#' }
	return "$scheme://$authority$remainder"
}

fun queryParameter(url: String, key: String): String? {
	val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
	if (query.isEmpty()) return null
	for (pair in query.split('&')) {
		val name = pair.substringBefore('=')
		if (decodeQueryComponent(name) == key) {
			return decodeQueryComponent(pair.substringAfter('=', ""))
		}
	}
	return null
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun encodeQueryComponent(value: String): String {
	val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
	val sb = StringBuilder()
	for (b in value.encodeToByteArray()) {
		val c = b.toInt().toChar()
		if (c in allowed) {
			sb.append(c)
		} else {
			sb.append('%')
			sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0xF])
			sb.append(HEX_DIGITS[b.toInt() and 0xF])
		}
	}
	return sb.toString()
}

private fun decodeQueryComponent(value: String): String {
	val sb = StringBuilder()
	var i = 0
	while (i < value.length) {
		val c = value[i]
		when {
			c == '+' -> {
				sb.append(' ')
				i++
			}

			c == '%' && i + 3 <= value.length -> {
				val code = value.substring(i + 1, i + 3).toIntOrNull(16)
				if (code != null) {
					sb.append(code.toChar())
					i += 3
				} else {
					sb.append(c)
					i++
				}
			}

			else -> {
				sb.append(c)
				i++
			}
		}
	}
	return sb.toString()
}
