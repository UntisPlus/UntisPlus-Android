package com.deelabs.untisplus.ui.pages.timetable

import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.KlasseEntity
import com.deelabs.untisplus.data.database.entities.RoomEntity
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.database.entities.SubjectEntity
import com.deelabs.untisplus.data.database.entities.TeacherEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserWithData
import com.deelabs.untisplus.data.repository.DefaultMasterDataRepository
import com.deelabs.untisplus.data.repository.ElementDisplayName
import com.deelabs.untisplus.data.repository.MasterDataRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimetableTitleTest {
	private val user = User(
		id = 1,
		apiHost = "example.invalid",
		timeGrid = TimeGrid(emptyList()),
		masterDataTimestamp = 0,
		userData = UserData(
			elemId = 100,
			elemType = ElementType.TEACHER,
			displayName = "Test Teacher",
			schoolName = "Test School",
			departmentId = 0,
			children = listOf(Person(id = 200, firstName = "Ada", lastName = "Lovelace")),
		),
	)

	private val userData = UserWithData(
		user = user,
		absenceReasons = emptyList(),
		departments = emptyList(),
		duties = emptyList(),
		eventReasons = emptyList(),
		eventReasonGroups = emptyList(),
		excuseStatuses = emptyList(),
		holidays = emptyList(),
		klassen = listOf(
			KlasseEntity(id = 10, userId = 1, name = "10aR", longName = "Klasse 10aR", active = true),
			KlasseEntity(id = 11, userId = 1, name = "11B", longName = "", active = false),
			KlasseEntity(id = 12, userId = 1, name = "12C", longName = "Klasse 12 C", active = false),
		),
		rooms = listOf(RoomEntity(id = 40, userId = 1, name = "R1", longName = "Raum 1", active = false)),
		subjects = listOf(SubjectEntity(id = 30, userId = 1, name = "MA", longName = "Mathematik", active = false)),
		teachers = listOf(TeacherEntity(id = 20, userId = 1, name = "LO", firstName = "Ada", lastName = "Lovelace", active = false)),
		teachingMethods = emptyList(),
		schoolYears = emptyList(),
	)

	private val repository = LocalMasterDataRepository(userData)
	private val personalElement = PeriodElement(ElementType.TEACHER, 100)
	private val profileTitle = "Test Teacher (1)"

	@Test
	fun `swapped class title uses the class full name instead of the profile name`() {
		assertEquals(
			"Klasse 10aR",
			timetableTitle(PeriodElement(ElementType.CLASS, 10), personalElement, repository, profileTitle)
		)
	}

	@Test
	fun `selected child timetable uses the child name instead of the guardian name`() {
		assertEquals(
			"Ada Lovelace",
			timetableTitle(PeriodElement(ElementType.STUDENT, 200), personalElement, repository, profileTitle)
		)
		assertEquals(
			"Ada Lovelace",
			timetableTitle(
				PeriodElement(ElementType.STUDENT, 200),
				personalElement,
				repository,
				profileTitle,
				mapOf(200L to "Ada Lovelace")
			)
		)
	}

	@Test
	fun `personal timetable title uses the profile name`() {
		assertEquals(
			profileTitle,
			timetableTitle(personalElement, personalElement, repository, profileTitle)
		)
		assertEquals(
			profileTitle,
			timetableTitle(null, personalElement, repository, profileTitle)
		)
	}

	@Test
	fun `inactive class still resolves its full name for the title`() {
		assertEquals(
			"Klasse 12 C",
			timetableTitle(PeriodElement(ElementType.CLASS, 12), personalElement, repository, profileTitle)
		)
	}

	@Test
	fun `blank long name falls back to the stored class name for the title`() {
		assertEquals(
			"11B",
			timetableTitle(PeriodElement(ElementType.CLASS, 11), personalElement, repository, profileTitle)
		)
	}

	@Test
	fun `genuinely unknown target keeps the v1_2_0 sentinel instead of the profile name`() {
		assertEquals(
			MasterDataRepository.ELEMENT_NAME_UNKNOWN,
			timetableTitle(PeriodElement(ElementType.CLASS, 999), personalElement, repository, profileTitle)
		)
	}

	private class LocalMasterDataRepository(
		private val localUserData: UserWithData?
	) : MasterDataRepository by DefaultMasterDataRepository() {
		override val user: User? get() = localUserData?.user
		override val userData: UserWithData? get() = localUserData

		override suspend fun schoolYears(): List<SchoolYearEntity> = localUserData?.schoolYears.orEmpty()

		override fun getShortName(id: Long, type: ElementType?, default: String): String =
			ElementDisplayName.shortName(id, type, localUserData) ?: default

		override fun getShortName(periodElement: PeriodElement, default: String) =
			getShortName(periodElement.id, periodElement.type, default)

		override fun getLongName(id: Long, type: ElementType, default: String): String =
			ElementDisplayName.longName(id, type, localUserData) ?: default

		override fun getLongName(periodElement: PeriodElement, default: String) =
			getLongName(periodElement.id, periodElement.type, default)
	}
}
