package com.deelabs.untisplus.ui.pages.settings

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.BuildConfig
import com.deelabs.untisplus.api.model.proxy.ProxyMe
import com.deelabs.untisplus.api.model.proxy.ProxyStatus
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_DEV_BRANCH
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_DEV_BRANCH_API
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_RELEASES_API
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_REPOSITORY_API
import com.deelabs.untisplus.data.model.github.GitHubCommit
import com.deelabs.untisplus.data.model.github.GitHubRelease
import com.deelabs.untisplus.data.model.github.GitHubUser
import com.deelabs.untisplus.data.repository.GlobalSettingsRepository
import com.deelabs.untisplus.helpers.AppVersion
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.services.AutoMuteService
import com.deelabs.untisplus.services.AutoMuteServiceZenRuleImpl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("NewApi")
class SettingsScreenViewModel constructor(
	globalSettingsRepository: GlobalSettingsRepository,
	private val userRepository: UserRepository,
	internal val userSettingsRepository: UserSettingsRepository,
	internal val autoMuteService: AutoMuteService,
	internal val savedStateHandle: SavedStateHandle,
	internal val masterDataRepository: MasterDataRepository,
	private val httpClient: HttpClient,
	val colorScheme: ColorScheme,
	private val proxyRepository: ProxyRepository,
) : ViewModel() {
	init {
		if (autoMuteService is AutoMuteServiceZenRuleImpl) {
			autoMuteService.setUser(userRepository.currentUser!!)
		}
		// Refresh the proxy tier/permissions for feature gating; ignore failures.
		if (proxyRepository.isAvailable) {
			viewModelScope.launch {
				runCatching { proxyRepository.refreshMe() }
			}
		}
	}

	val globalRepository = globalSettingsRepository

	private val _contributors = MutableStateFlow<List<GitHubUser>>(emptyList())
	val contributors: StateFlow<List<GitHubUser>> = _contributors

	private val _contributorsError = MutableStateFlow<Throwable?>(null)
	val contributorsError: StateFlow<Throwable?> = _contributorsError

	fun resetColors() = viewModelScope.launch {
		userSettingsRepository.updateSettings {
			clearBackgroundRegular()
			clearBackgroundRegularPast()
			clearBackgroundExam()
			clearBackgroundExamPast()
			clearBackgroundIrregular()
			clearBackgroundIrregularPast()
			clearBackgroundCancelled()
			clearBackgroundCancelledPast()
		}
	}

	fun currentUserId() = userRepository.currentUser?.id

	/** Whether the untis-proxy features apply to the active account. */
	val proxyAvailable: Boolean
		get() = proxyRepository.isAvailable

	/** The active user's person id, for a personal (STUDENT) stream target. */
	val proxyPersonId: Long?
		get() = userRepository.currentUser?.userData?.elemId?.takeIf { it > 0 }

	/** Full `/me` payload for the capability controls. */
	val proxyMe: StateFlow<ProxyMe?> = proxyRepository.me

	suspend fun refreshProxyMe(): Result<ProxyMe> = runCatching { proxyRepository.refreshMe() }

	suspend fun setProxyCapability(capability: String, enabled: Boolean): Result<ProxyMe> =
		runCatching { proxyRepository.updateCapability(capability, enabled) }

	/** Server health plus the active account's tier, for the proxy diagnostics menu. */
	suspend fun proxyDiagnostics(): Result<ProxyDiagnostics> = runCatching {
		ProxyDiagnostics(
			status = proxyRepository.status(),
			me = proxyRepository.refreshMe()
		)
	}

	data class ProxyDiagnostics(val status: ProxyStatus, val me: ProxyMe)

	sealed interface UpdateCheckResult {
		data class ReleaseAvailable(
			val tag: String,
			val htmlUrl: String,
			val downloadUrl: String?,
			val notes: String?,
		) : UpdateCheckResult

		data class DevUpdateAvailable(
			val remoteSha: String,
			val branchUrl: String,
		) : UpdateCheckResult

		data object UpToDate : UpdateCheckResult

		data class Failed(val reason: String?) : UpdateCheckResult
	}

	/**
	 * Stable builds compare against the latest GitHub release. Debug builds
	 * compare their embedded commit against the dev branch, since dev builds
	 * are not published as releases.
	 */
	suspend fun checkForUpdates(): UpdateCheckResult = runCatching {
		if (BuildConfig.DEBUG) checkDevUpdates() else checkReleaseUpdates()
	}.getOrElse { UpdateCheckResult.Failed(it.message) }

	private suspend fun checkReleaseUpdates(): UpdateCheckResult {
		val release = httpClient.get(URL_GITHUB_RELEASES_API).body<GitHubRelease>()
		return if (AppVersion.isNewer(release.tagName, BuildConfig.RELEASE_VERSION_NAME)) {
			UpdateCheckResult.ReleaseAvailable(
				tag = release.tagName,
				htmlUrl = release.htmlUrl,
				downloadUrl = release.assets
					.firstOrNull { it.name.equals("UntisPlus.apk", ignoreCase = true) }
					?.browserDownloadUrl,
				notes = release.body,
			)
		} else {
			UpdateCheckResult.UpToDate
		}
	}

	private suspend fun checkDevUpdates(): UpdateCheckResult {
		val localSha = BuildConfig.GIT_COMMIT_SHA
		if (localSha.isBlank() || localSha == "unknown") {
			return UpdateCheckResult.Failed("This build has no git commit information")
		}
		val commit = httpClient.get(URL_GITHUB_DEV_BRANCH_API).body<GitHubCommit>()
		return if (!commit.sha.startsWith(localSha)) {
			UpdateCheckResult.DevUpdateAvailable(
				remoteSha = commit.sha.take(7),
				branchUrl = URL_GITHUB_DEV_BRANCH,
			)
		} else {
			UpdateCheckResult.UpToDate
		}
	}

	suspend fun loadContributors() {
		_contributorsError.value = null

		try {
			_contributors.value = httpClient.get("$URL_GITHUB_REPOSITORY_API/contributors").body()
		} catch (e: Exception) {
			_contributorsError.value = e
		}
	}
}
