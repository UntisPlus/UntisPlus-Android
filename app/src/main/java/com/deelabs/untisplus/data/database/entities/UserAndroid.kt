package com.deelabs.untisplus.data.database.entities

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.SchoolInfo

/**
 * Android-only members of [User]: URL building uses [android.net.Uri] and the
 * display name overloads need Android resources/Compose. They live here rather
 * than in the shared entity so the entity itself stays multiplatform.
 */

const val UNTISPLUS_API_HOST = "api-untis.deelabs.tech"

/**
 * Normalizes a user-provided API endpoint to a scheme/authority pair.
 * A missing scheme defaults to https; http is allowed for custom endpoints only.
 */
fun normalizeApiEndpoint(raw: String): Pair<String, String>? {
	val trimmed = raw.trim()
	if (trimmed.isEmpty()) return null
	val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
	val uri = runCatching { withScheme.toUri() }.getOrNull() ?: return null
	val authority = uri.encodedAuthority ?: return null
	if (uri.host.isNullOrBlank() || authority.isBlank()) return null
	return (uri.scheme?.lowercase() ?: "https") to authority
}

fun buildApiUrl(
	apiHost: String,
	schoolInfo: SchoolInfo? = null
): Uri {
	val raw = apiHost.ifBlank {
		if (schoolInfo?.useMobileServiceUrlAndroid == true && !schoolInfo.mobileServiceUrl.isNullOrBlank()) schoolInfo.mobileServiceUrl
		else schoolInfo?.serverUrl
	}!!

	return if (raw.contains("://")) {
		val uri = raw.toUri()
		Uri.Builder()
			.scheme(uri.scheme ?: "https")
			.encodedAuthority(uri.encodedAuthority)
			.appendPath("WebUntis")
			.build()
	} else {
		Uri.Builder()
			.scheme("https")
			.authority(raw)
			.appendPath("WebUntis")
			.build()
	}
}

fun buildJsonRpcApiUrl(apiUrl: Uri, schoolName: String): Uri {
	return apiUrl.buildUpon()
		.appendEncodedPath("jsonrpc_intern.do")
		.appendQueryParameter("school", schoolName)
		.build()
}

private fun User.resolveApiEndpoint(url: Uri): Uri {
	customApiHost?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
		normalizeApiEndpoint(raw)?.let { (scheme, authority) ->
			return url.buildUpon()
				.scheme(scheme)
				.encodedAuthority(authority)
				.build()
		}
	}
	if (useUntisPlusApi)
		return url.buildUpon()
			.scheme("https")
			.encodedAuthority(UNTISPLUS_API_HOST)
			.build()
	return url
}

fun User.getDisplayedName(context: Context): String {
	return when {
		profileName.isNotBlank() -> profileName
		anonymous -> context.getString(R.string.all_anonymous)
		else -> userData.displayName
	}
}

@Composable
fun User.getDisplayedName(): String {
	return when {
		profileName.isNotBlank() -> profileName
		anonymous -> stringResource(R.string.all_anonymous)
		else -> userData.displayName
	}
}

val User.apiUrl: Uri
	get() = buildApiUrl(apiHost, schoolInfo)

val User.jsonRpcApiUrl: Uri
	get() {
		val schoolName = schoolInfo?.loginName ?: apiHost.toUri().getQueryParameter("school") ?: ""
		return resolveApiEndpoint(buildJsonRpcApiUrl(apiUrl, schoolName))
	}

/**
 * Origin (scheme + authority, no path, no trailing slash) of the resolved API
 * host — the base for the proxy's own endpoints (`/status`, `/me`, and the
 * `/api/…` REST surface). This is deliberately not [restApiUrl], which is the
 * upstream `/WebUntis/api/rest` path. Only meaningful when
 * [User.usesUntisPlusProxy] is true. See `docs/PROXY-API.md` §1.
 */
val User.proxyBaseUrl: Uri
	get() = jsonRpcApiUrl.buildUpon()
		.path(null)
		.clearQuery()
		.fragment(null)
		.build()

/**
 * The `?school=` value for the proxy's own endpoints. Mirrors the school name
 * used by [jsonRpcApiUrl]. See `docs/PROXY-API.md` §1.
 */
val User.proxySchoolName: String?
	get() = (schoolInfo?.loginName
		?: apiHost.toUri().getQueryParameter("school"))
		?.takeIf { it.isNotBlank() }

val User.restApiUrl: Uri
	get() = apiUrl.buildUpon()
		.appendEncodedPath("api/rest")
		.build()

val User.restApiAuthUrl: Uri
	get() = apiUrl.buildUpon()
		.appendEncodedPath("api/mobile/v2")
		.build()
