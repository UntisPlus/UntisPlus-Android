package com.deelabs.untisplus.ui.pages.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.activities.main.NavItemNavigation
import com.deelabs.untisplus.api.model.untis.enumeration.Right
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.repository.CustomLink
import com.deelabs.untisplus.data.repository.CustomLinksRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.data.settings.model.TimetableElement
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.preferences.toTimetableElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimetableDrawerViewModel constructor(
	private val userSettingsRepository: UserSettingsRepository,
	private val navigator: AppNavigator,
	private val masterDataRepository: MasterDataRepository,
	private val proxyRepository: ProxyRepository,
	private val customLinksRepository: CustomLinksRepository,
	private val userRepository: UserRepository,
) : ViewModel() {
	var enableDrawerGestures: Boolean = true
		private set

	/** Only proxies answering `/me` expose the teacher-absences report tab. */
	var teacherAbsencesAvailable by mutableStateOf(false)
		private set

	var bookmarkDeleteDialog by mutableStateOf<PeriodElement?>(null)
		private set

	private val _bookmarks = MutableStateFlow<List<TimetableElement>>(emptyList())
	val bookmarks: StateFlow<List<TimetableElement>> = _bookmarks

	private val _customLinks = MutableStateFlow<List<CustomLink>>(emptyList())
	val customLinks: StateFlow<List<CustomLink>> = _customLinks

	/** Teacher-only class register tools, from the user's Untis rights. */
	val showClassTools: Boolean
		get() = Right.CLASSREGISTER in userRepository.currentUser?.userData?.rights.orEmpty()

	init {
		viewModelScope.launch {
			userSettingsRepository.getSettings().collect {
				it.themeColor
				_bookmarks.value = it.bookmarksList
			}
		}
		viewModelScope.launch {
			proxyRepository.teacherAbsencesSupported.collect { teacherAbsencesAvailable = it }
		}
		viewModelScope.launch {
			customLinksRepository.load()
				.onSuccess { _customLinks.value = it }
				.onFailure { _customLinks.value = emptyList() }
		}
	}

	fun onNavigationItemClick(item: NavItemNavigation) {
		navigator.navigate(item.route)
	}

	fun onBookmarkClick(bookmark: PeriodElement) {
		navigator.navigate(AppRoutes.Timetable(bookmark.type, bookmark.id))
	}

	fun addBookmark(item: PeriodElement) = viewModelScope.launch {
		userSettingsRepository.updateSettings {
			if (!bookmarksList.any { it.matches(item) }) {
				addBookmarks(item.toTimetableElement())
			}
		}
	}

	fun removeBookmark(bookmark: PeriodElement) = viewModelScope.launch {
		userSettingsRepository.updateSettings {
			removeBookmarks(bookmarksList.indexOfFirst { it.matches(bookmark) })
		}
		dismissBookmarkDeleteDialog()
	}

	fun showBookmarkDeleteDialog(bookmark: PeriodElement) {
		bookmarkDeleteDialog = bookmark
	}

	fun dismissBookmarkDeleteDialog() {
		bookmarkDeleteDialog = null
	}

	fun getBookmarkDisplayName(bookmark: PeriodElement): String {
		return masterDataRepository.getLongName(bookmark)
	}
}

private fun TimetableElement.matches(bookmark: PeriodElement): Boolean {
	return elementType == bookmark.type.id && elementId == bookmark.id
}
