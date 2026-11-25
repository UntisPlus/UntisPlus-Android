package com.deelabs.untisplus.ui.pages.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.activities.main.NavItemNavigation
import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.Right
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.repository.ChildSelectionRepository
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
	private val childSelectionRepository: ChildSelectionRepository,
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

	/** Children of a parent/guardian account, used as timetable targets. */
	val children: List<Person>
		get() = userRepository.currentUser?.userData?.children?.filterNotNull().orEmpty()

	private val isParent: Boolean
		get() = userRepository.currentUser?.userData?.isParentAccount() == true

	val selectedChildId: StateFlow<Long?> = childSelectionRepository.selectedChildId

	/** Parents only get the personal timetable shortcut once configured in settings. */
	private val _showPersonalTimetable = MutableStateFlow(!isParent)
	val showPersonalTimetable: StateFlow<Boolean> = _showPersonalTimetable

	/** Teacher-only class register tools; parent/guardian accounts never get them. */
	val showClassTools: Boolean
		get() = !isParent && Right.CLASSREGISTER in userRepository.currentUser?.userData?.rights.orEmpty()

	init {
		if (children.isNotEmpty() && selectedChildId.value !in children.map { it.id })
			childSelectionRepository.selectChild(children.first().id)

		viewModelScope.launch {
			userSettingsRepository.getSettings().collect {
				it.themeColor
				_bookmarks.value = it.bookmarksList
				_showPersonalTimetable.value = !isParent || it.hasTimetablePersonalTimetable()
			}
		}
		viewModelScope.launch {
			proxyRepository.teacherAbsencesSupported.collect { teacherAbsencesAvailable = !isParent && it }
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

	fun onChildClick(child: Person) {
		childSelectionRepository.selectChild(child.id)
		navigator.navigate(AppRoutes.Timetable(ElementType.STUDENT, child.id))
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
