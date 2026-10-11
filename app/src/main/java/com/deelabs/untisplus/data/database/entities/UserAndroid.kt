package com.deelabs.untisplus.data.database.entities

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.SchoolInfo

/**
 * Android-only members of [User]. The URL logic itself lives in `UserUrl.kt`
 * (multiplatform, string based); these wrappers expose it as [Uri] for the
 * Android UI, plus the resource-dependent display-name overloads.
 */

fun buildApiUrl(
	apiHost: String,
	schoolInfo: SchoolInfo? = null
): Uri = buildApiUrlString(apiHost, schoolInfo).toUri()

fun buildJsonRpcApiUrl(apiUrl: Uri, schoolName: String): Uri =
	buildJsonRpcApiUrlString(apiUrl.toString(), schoolName).toUri()

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
	get() = apiUrlString.toUri()

val User.jsonRpcApiUrl: Uri
	get() = jsonRpcApiUrlString.toUri()

/**
 * Origin (scheme + authority, no path, no trailing slash) of the resolved API
 * host — the base for the proxy's own endpoints (`/status`, `/me`, and the
 * `/api/…` REST surface). Only meaningful when [User.usesUntisPlusProxy] is
 * true. See `docs/PROXY-API.md` §1.
 */
val User.proxyBaseUrl: Uri
	get() = proxyBaseUrlString.toUri()

/** The `?school=` value for the proxy's own endpoints. See `docs/PROXY-API.md` §1. */
val User.proxySchoolName: String?
	get() = proxySchoolNameValue

val User.restApiUrl: Uri
	get() = restApiUrlString.toUri()

val User.restApiAuthUrl: Uri
	get() = restApiAuthUrlString.toUri()
