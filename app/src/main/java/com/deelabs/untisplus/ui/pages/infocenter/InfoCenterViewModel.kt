package com.deelabs.untisplus.ui.pages.infocenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deelabs.untisplus.api.model.response.OfficeHourRegistrationsResult
import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.classreg.Exam
import com.deelabs.untisplus.api.model.untis.classreg.HomeWork
import com.deelabs.untisplus.api.model.untis.absence.StudentAbsence
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.enumeration.Right
import com.deelabs.untisplus.api.model.untis.timetable.OfficeHour
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.data.repository.ChildSelectionRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.domain.GetAbsencesUseCase
import com.deelabs.untisplus.domain.GetExamsUseCase
import com.deelabs.untisplus.domain.GetHomeworkUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCase
import com.deelabs.untisplus.domain.GetMessagesUseCase
import com.deelabs.untisplus.domain.GetOfficeHourRegistrationsUseCase
import com.deelabs.untisplus.domain.GetOfficeHoursUseCase
import com.deelabs.untisplus.model.rest.Message
import com.deelabs.untisplus.ui.navigation.AppNavigator
import com.deelabs.untisplus.ui.pages.infocenter.fragments.AbsencesUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.EventItem
import com.deelabs.untisplus.ui.pages.infocenter.fragments.EventsUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.MessagesUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.OfficeHourRegistrationUiState
import com.deelabs.untisplus.ui.pages.infocenter.fragments.OfficeHoursUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import crocodile8.universal_cache.FromCache
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate

class InfoCenterViewModel constructor(
	private val userRepository: UserRepository,
	internal val masterDataRepository: MasterDataRepository,
	private val navigator: AppNavigator,
	internal val userSettingsRepository: UserSettingsRepository,
	private val getMessages: GetMessagesUseCase,
	getMessagesOfDay: GetMessagesOfDayUseCase,
	private val getExams: GetExamsUseCase,
	private val getHomework: GetHomeworkUseCase,
	getAbsences: GetAbsencesUseCase,
	getOfficeHours: GetOfficeHoursUseCase,
	getOfficeHourRegistrations: GetOfficeHourRegistrationsUseCase,
	private val proxyRepository: ProxyRepository,
	private val childSelectionRepository: ChildSelectionRepository,
	private val timetableRepository: TimetableRepository,
) : ViewModel() {
	private val excuseStatuses = masterDataRepository.userData?.excuseStatuses ?: emptyList()

	private companion object {
		const val ONE_HOUR = 60 * 60 * 1000L
	}

	/** Children available to a parent/guardian account. */
	val children: List<Person>
		get() = userRepository.currentUser?.userData?.children?.filterNotNull().orEmpty()

	private val isParent: Boolean
		get() = userRepository.currentUser?.userData?.isParentAccount() == true

	val selectedChildId: StateFlow<Long?> = childSelectionRepository.selectedChildId

	/** Whether the untis-proxy features (homework flags, absence notes) apply. */
	val proxyAvailable: Boolean
		get() = proxyRepository.isAvailable

	/**
	 * Optimistic local overlay of homework "done" flags so the checkbox flips
	 * immediately; the proxy's injected value wins once the list is reloaded.
	 */
	private val homeworkDoneOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

	/** Optimistic local overlay of absence notes, keyed by absence id. */
	private val absenceNoteOverrides = MutableStateFlow<Map<Long, String?>>(emptyMap())

	/** Server-side values from the proxy, used when there is no local override. */
	private val proxyHomeworkFlags = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
	private val proxyAbsenceNotes = MutableStateFlow<Map<Long, String?>>(emptyMap())

	init {
		if (isParent && childSelectionRepository.selectedChildId.value !in children.map { it.id })
			childSelectionRepository.selectChild(children.first().id)

		if (proxyAvailable) {
			viewModelScope.launch { loadProxyState() }
		}
	}

	fun selectChild(id: Long?) = childSelectionRepository.selectChild(id)

	/** Resolves a child's class for the office-hours request; -1 keeps the account default. */
	private suspend fun officeHoursClassId(childId: Long?): Long {
		val childId = childId ?: return -1L
		val user = userRepository.currentUser ?: return -1L
		val periods = runCatching {
			timetableRepository.timetableSource().get(
				TimetableRepository.TimetableParams(childId, ElementType.STUDENT, java.time.LocalDate.now().toKotlinLocalDate()),
				FromCache.CACHED_THEN_LOAD,
				maxAge = ONE_HOUR,
				additionalKey = user.id
			).last()
		}.getOrDefault(emptyList<Period>())
		return periods.firstNotNullOfOrNull { period ->
			period.elements.firstOrNull { it.type == ElementType.CLASS }?.id
		} ?: user.userData.klassenIds.firstOrNull() ?: -1L
	}

	private suspend fun loadProxyState() {
		loadHomeworkFlags()
		loadAbsenceNotes()
	}

	private suspend fun loadHomeworkFlags() {
		runCatching { proxyRepository.homeworkFlags() }
			.onSuccess { flags ->
				proxyHomeworkFlags.value = flags.flags.associate { it.homeworkId to it.done }
			}
	}

	private suspend fun loadAbsenceNotes() {
		runCatching { proxyRepository.absenceNotes() }
			.onSuccess { notes ->
				proxyAbsenceNotes.value = notes.notes.associate { it.absenceKey to it.note }
			}
	}

	val shouldShowAbsences: Boolean
		get() = isParent || Right.R_MY_ABSENCES in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAdd: Boolean
		get() = Right.W_OWN_ABSENCE in userRepository.currentUser!!.userData.rights
	val shouldShowAbsencesAddReason: Boolean
		get() = Right.W_OWN_ABSENCEREASON in userRepository.currentUser!!.userData.rights

	val shouldShowOfficeHours: Boolean
		get() = isParent || Right.R_OFFICEHOURS in userRepository.currentUser!!.userData.rights

	val messagesState: StateFlow<MessagesUiState> = combine(
		getMessagesOfDay(),
		if (userRepository.currentUser!!.anonymous)
			flowOf(Result.success(emptyList()))
		else
			getMessages(),
		MessagesUiState::Success
	).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = MessagesUiState.Loading
	)

	private val _selectedMessage = MutableStateFlow<Message?>(null)
	val selectedMessage: StateFlow<Message?> = _selectedMessage
	private val _selectedMessageContent = MutableStateFlow<String?>(null)
	val selectedMessageContent: StateFlow<String?> = _selectedMessageContent

	fun onMessageClicked(message: Message) {
		_selectedMessage.value = message
		viewModelScope.launch {
			getMessages(message.id)
				.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
				.collect { result ->
					result?.fold(
						onSuccess = { _selectedMessageContent.value = it.content },
						onFailure = {
							_selectedMessage.value = null
						} // TODO: Instead of having `null` as loading, use a sealed class to handle error responses as well.
					)
				}
		}
	}

	fun onMessageDismiss() {
		_selectedMessage.value = null
	}

	fun onMessageReply() {
		_selectedMessage.value = null
	}

	fun onMessageDelete() {
		_selectedMessage.value = null
	}

	private data class EventElement(val id: Long, val type: ElementType, val name: String?)
	private data class ChildEvents(
		val exams: Result<List<Exam>>,
		val homework: Result<List<HomeWork>>
	)

	/** Parent accounts query each child as a STUDENT; everyone else uses their own element. */
	private val eventElements: List<EventElement>
		get() {
			val user = userRepository.currentUser!!
			return if (isParent)
				children.map { EventElement(it.id, ElementType.STUDENT, it.fullName()) }
			else
				listOf(EventElement(user.userData.elemId, user.userData.elemType ?: ElementType.STUDENT, null))
		}

	private fun eventsFor(element: EventElement): Flow<ChildEvents> = combine(
		getExams(element.id, element.type),
		getHomework(element.id, element.type),
		homeworkDoneOverrides,
		proxyHomeworkFlags
	) { exams, homework, overrides, remoteFlags ->
		ChildEvents(
			exams = exams,
			homework = homework.map { list ->
				list.map { item ->
					item.copy(done = overrides[item.id] ?: remoteFlags[item.id] ?: item.done)
				}
			}
		)
	}

	private fun <T> aggregateEvents(
		elements: List<EventElement>,
		results: List<ChildEvents>,
		selector: (ChildEvents) -> Result<List<T>>
	): Result<List<EventItem<T>>> {
		val successful = results.mapIndexedNotNull { index, result ->
			selector(result).getOrNull()?.map { EventItem(it, elements[index].name) }
		}
		return if (successful.isEmpty())
			selector(results.first()).map { emptyList() }
		else
			Result.success(successful.flatten())
	}

	/** `null` is the All page; child ids map to their own page. */
	val eventsPages: StateFlow<Map<Long?, EventsUiState>> = run {
		val elements = eventElements
		if (elements.isEmpty())
			flowOf<Map<Long?, EventsUiState>>(mapOf(null to EventsUiState.Loading))
		else
			combine(elements.map(::eventsFor)) { values ->
				val results = values.map { it as ChildEvents }
				buildMap<Long?, EventsUiState> {
					put(
						null,
						EventsUiState.Success(
							exams = aggregateEvents(elements, results) { it.exams },
							homework = aggregateEvents(elements, results) { it.homework }
						)
					)
					elements.forEachIndexed { index, element ->
						put(
							element.id,
							EventsUiState.Success(
								exams = results[index].exams.map { list -> list.map { EventItem(it) } },
								homework = results[index].homework.map { list -> list.map { EventItem(it) } }
							)
						)
					}
				}
			}
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = emptyMap()
	)

	/** Toggles a homework "done" flag via the proxy, optimistically. */
	fun setHomeworkDone(item: HomeWork, done: Boolean) {
		if (!proxyRepository.isAvailable) return
		homeworkDoneOverrides.update { it + (item.id to done) }
		viewModelScope.launch {
			runCatching { proxyRepository.setHomeworkDone(item.id, done) }
				.onSuccess { loadHomeworkFlags() }
				.onFailure { homeworkDoneOverrides.update { it - item.id } }
		}
	}

	val absencesState: StateFlow<AbsencesUiState> = combine(
		if (!shouldShowAbsences) emptyFlow() else getAbsences(),
		absenceNoteOverrides,
		proxyAbsenceNotes
	) { result, noteOverrides, remoteNotes ->
		AbsencesUiState.Success(
			result.map { list ->
				list.map { absence ->
					when {
						noteOverrides.containsKey(absence.id) ->
							absence.copy(note = noteOverrides[absence.id])
						remoteNotes.containsKey(absence.id) ->
							absence.copy(note = remoteNotes[absence.id])
						else -> absence
					}
				}
			},
			excuseStatuses
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = AbsencesUiState.Loading
	)

	/** Saves (or clears, when blank) an absence note via the proxy, optimistically. */
	fun setAbsenceNote(item: StudentAbsence, note: String) {
		if (!proxyRepository.isAvailable) return
		val trimmed = note.trim()
		absenceNoteOverrides.update { it + (item.id to trimmed.ifEmpty { null }) }
		viewModelScope.launch {
			runCatching { proxyRepository.setAbsenceNote(item.id, trimmed) }
				.onSuccess { loadAbsenceNotes() }
				.onFailure { absenceNoteOverrides.update { it - item.id } }
		}
	}

	private suspend fun officeHoursClassIds(): List<Long> {
		if (!isParent) return listOf(-1L)
		val ids = children.map { officeHoursClassId(it.id) }.distinct()
		return ids.ifEmpty { listOf(-1L) }
	}

	private fun aggregateOfficeHours(results: List<Result<List<OfficeHour>>>): Result<List<OfficeHour>> {
		val successful = results.mapNotNull { it.getOrNull() }
		return if (successful.isEmpty())
			results.first().map { emptyList() }
		else
			Result.success(
				successful.flatten()
					.distinctBy { it.id }
					.sortedBy { it.startDateTime.toJavaLocalDateTime() }
			)
	}

	/** One combined list, covering every child for parent accounts. */
	val officeHoursState: StateFlow<OfficeHoursUiState> =
		(if (!shouldShowOfficeHours) emptyFlow<Result<List<OfficeHour>>>()
		else flow { emit(officeHoursClassIds()) }
			.flatMapLatest { classIds ->
				combine(classIds.map { klasseId -> getOfficeHours(klasseId) }) { results ->
					aggregateOfficeHours(results.map { it as Result<List<OfficeHour>> })
				}
			})
			.map(OfficeHoursUiState::Success)
			.stateIn(
				scope = viewModelScope,
				started = SharingStarted.WhileSubscribed(5_000),
				initialValue = OfficeHoursUiState.Loading
			)

	private val _selectedOfficeHour = MutableStateFlow<OfficeHour?>(null)
	val selectedOfficeHour: StateFlow<OfficeHour?> = _selectedOfficeHour.asStateFlow()

	/** Timeslots for the tapped office hour; fetched only while a page is open. */
	val officeHourRegistrationState: StateFlow<OfficeHourRegistrationUiState> =
		_selectedOfficeHour.flatMapLatest { officeHour ->
			if (officeHour == null)
				flowOf(OfficeHourRegistrationUiState.Loading)
			else
				getOfficeHourRegistrations(officeHour)
					.map<Result<OfficeHourRegistrationsResult>, OfficeHourRegistrationUiState> {
						OfficeHourRegistrationUiState.Success(it)
					}
					.onStart { emit(OfficeHourRegistrationUiState.Loading) }
		}.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = OfficeHourRegistrationUiState.Loading
		)

	fun onOfficeHourSelected(officeHour: OfficeHour?) {
		_selectedOfficeHour.value = officeHour
	}

	fun onOfficeHourDismiss() = onOfficeHourSelected(null)

	fun goBack() {
		navigator.popBackStack()
	}
}
