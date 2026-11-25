package com.deelabs.untisplus.widgets

import android.content.Context
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.Period
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserWithData
import com.deelabs.untisplus.data.database.entities.getDisplayedName
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.models.PeriodItem
import com.deelabs.untisplus.workers.TimetableDependantWorker
import crocodile8.universal_cache.FromCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class WidgetLesson(
	val time: String,
	val subject: String,
	val detail: String,
)

internal data class WidgetTimetable(
	val title: String = "",
	val lessons: List<WidgetLesson> = emptyList(),
	val message: String? = null,
)

/** Loads the widget's timetable for the profile stored with that widget. */
internal class WidgetTimetableLoader : KoinComponent {
	private val userDao by inject<com.deelabs.untisplus.data.database.entities.UserDao>()
	private val timetableRepository by inject<TimetableRepository>()
	private val userSettingsRepository by inject<UserSettingsRepository>()

	suspend fun load(context: Context, configuration: WidgetConfiguration): WidgetTimetable {
		val user = userDao.getById(configuration.userId)
			?: return WidgetTimetable(message = context.getString(R.string.widget_timetable_no_profile))

		val element = configuration.element
			?: TimetableDependantWorker.getPersonalTimetableElement(
				user,
				userSettingsRepository.getSettings(user.id).first()
			)
			?: return WidgetTimetable(
				title = context.getString(R.string.widget_timetable),
				message = context.getString(R.string.widget_timetable_no_element)
			)

		val masterData = WidgetMasterData(userDao.getByIdWithData(user.id))
		val today = LocalDate.now().toKotlinLocalDate()
		val params = TimetableRepository.TimetableParams(
			elementId = element.id,
			elementType = element.type,
			startDate = today,
			endDate = today,
		)
		val source = timetableRepository.timetableSource(user)

		// Prefer fresh data, but keep showing the last cached timetable if the
		// network is unavailable. Nothing private is logged here.
		val periods: List<Period> = runCatching {
			source.get(
				params = params,
				fromCache = FromCache.CACHED_THEN_LOAD,
				maxAge = 10 * 60 * 1000,
				additionalKey = user.id
			).last()
		}.getOrNull() ?: runCatching {
			source.get(
				params = params,
				fromCache = FromCache.ONLY,
				additionalKey = user.id
			).last()
		}.getOrNull() ?: emptyList()

		val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
		val lessons = periods
			.map { PeriodItem(masterData, it) }
			.filterNot { it.isCancelled() }
			.sortedBy { it.originalPeriod.startDateTime }
			.map { item ->
				val subject = item.getShort(ElementType.SUBJECT)
					.ifBlank { item.originalPeriod.text.lesson }
					.ifBlank { context.getString(R.string.widget_timetable_lesson_unknown) }
				val details = listOf(item.getShort(ElementType.ROOM), item.getShort(ElementType.TEACHER))
					.filter { it.isNotBlank() }
					.joinToString(" · ")
				WidgetLesson(
					time = item.originalPeriod.startDateTime.toJavaLocalDateTime().format(timeFormatter),
					subject = subject,
					detail = details,
				)
			}

		val title = masterData.getShortName(element, user.getDisplayedName(context))
		return if (lessons.isEmpty()) {
			WidgetTimetable(
				title = title,
				message = context.getString(R.string.widget_timetable_empty)
			)
		} else {
			WidgetTimetable(title = title, lessons = lessons)
		}
	}
}

/** Read-only master data for the profile the widget was configured with. */
private class WidgetMasterData(
	private val data: UserWithData?,
) : MasterDataRepository {
	override val user: User? get() = data?.user
	override val userData: UserWithData? get() = data

	override suspend fun schoolYears() = data?.schoolYears.orEmpty()

	override val allClasses = MutableStateFlow(elements(ElementType.CLASS))
	override val allTeachers = MutableStateFlow(elements(ElementType.TEACHER))
	override val allSubjects = MutableStateFlow(elements(ElementType.SUBJECT))
	override val allRooms = MutableStateFlow(elements(ElementType.ROOM))

	private fun elements(type: ElementType): List<PeriodElement> = when (type) {
		ElementType.CLASS -> data?.klassen.orEmpty().map { PeriodElement(type, it.id) }
		ElementType.TEACHER -> data?.teachers.orEmpty().map { PeriodElement(type, it.id) }
		ElementType.SUBJECT -> data?.subjects.orEmpty().map { PeriodElement(type, it.id) }
		ElementType.ROOM -> data?.rooms.orEmpty().map { PeriodElement(type, it.id) }
		else -> emptyList()
	}

	private fun shortName(id: Long, type: ElementType?): String? = when (type) {
		ElementType.CLASS -> data?.klassen?.firstOrNull { it.id == id }?.name
		ElementType.TEACHER -> data?.teachers?.firstOrNull { it.id == id }?.name
		ElementType.SUBJECT -> data?.subjects?.firstOrNull { it.id == id }?.name
		ElementType.ROOM -> data?.rooms?.firstOrNull { it.id == id }?.name
		ElementType.STUDENT -> data?.user?.userData?.children?.firstOrNull { it?.id == id }?.fullName()
		else -> null
	}

	private fun longName(id: Long, type: ElementType): String? = when (type) {
		ElementType.CLASS -> data?.klassen?.firstOrNull { it.id == id }?.longName
		ElementType.TEACHER -> data?.teachers?.firstOrNull { it.id == id }?.let { "${it.firstName} ${it.lastName}".trim() }
		ElementType.SUBJECT -> data?.subjects?.firstOrNull { it.id == id }?.longName
		ElementType.ROOM -> data?.rooms?.firstOrNull { it.id == id }?.longName
		ElementType.STUDENT -> data?.user?.userData?.children?.firstOrNull { it?.id == id }?.fullName()
		else -> null
	}

	override fun getShortName(id: Long, type: ElementType?, default: String): String =
		shortName(id, type) ?: default

	override fun getShortName(periodElement: PeriodElement, default: String): String =
		getShortName(periodElement.id, periodElement.type, default)

	override fun getLongName(id: Long, type: ElementType, default: String): String =
		longName(id, type) ?: default

	override fun getLongName(periodElement: PeriodElement, default: String): String =
		getLongName(periodElement.id, periodElement.type, default)

	override fun isAllowed(id: Long, type: ElementType?): Boolean = true

	override fun isAllowed(periodElement: PeriodElement): Boolean = true
}
