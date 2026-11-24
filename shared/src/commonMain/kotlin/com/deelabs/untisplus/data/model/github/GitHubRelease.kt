package com.deelabs.untisplus.data.model.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The subset of the GitHub release response needed for update checks. */
@Serializable
data class GitHubRelease(
	@SerialName("tag_name") val tagName: String,
	@SerialName("html_url") val htmlUrl: String,
	@SerialName("published_at") val publishedAt: String? = null,
	val name: String? = null,
	val body: String? = null,
	val prerelease: Boolean = false,
	val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
	val name: String,
	@SerialName("browser_download_url") val browserDownloadUrl: String
)

/** The subset of the GitHub commit response needed for dev-channel checks. */
@Serializable
data class GitHubCommit(
	val sha: String
)
