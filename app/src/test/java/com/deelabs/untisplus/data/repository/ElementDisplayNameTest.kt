package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.model.untis.Person
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.KlasseEntity
import com.deelabs.untisplus.data.database.entities.RoomEntity
import com.deelabs.untisplus.data.database.entities.SubjectEntity
import com.deelabs.untisplus.data.database.entities.TeacherEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserWithData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ElementDisplayNameTest {
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
			KlasseEntity(id = 10, userId = 1, name = "10A", longName = "Klasse 10 A", active = false),
			KlasseEntity(id = 11, userId = 1, name = "11B", longName = ""),
		),
		rooms = listOf(RoomEntity(id = 40, userId = 1, name = "R1", longName = "Raum 1", active = false)),
		subjects = listOf(SubjectEntity(id = 30, userId = 1, name = "MA", longName = "Mathematik", active = false)),
		teachers = listOf(
			TeacherEntity(id = 20, userId = 1, name = "LO", firstName = "Ada", lastName = "Lovelace", active = false),
			TeacherEntity(id = 21, userId = 1, name = "XY", firstName = "", lastName = ""),
		),
		teachingMethods = emptyList(),
		schoolYears = emptyList(),
	)

	@Test
	fun `inactive class still resolves its full name`() {
		val element = PeriodElement(ElementType.CLASS, 10)

		assertEquals("Klasse 10 A", ElementDisplayName.longName(element, userData))
		assertEquals("10A", ElementDisplayName.shortName(element, userData))
	}

	@Test
	fun `inactive teacher and room resolve their full names`() {
		assertEquals(
			"Ada Lovelace",
			ElementDisplayName.longName(PeriodElement(ElementType.TEACHER, 20), userData)
		)
		assertEquals(
			"Raum 1",
			ElementDisplayName.longName(PeriodElement(ElementType.ROOM, 40), userData)
		)
	}

	@Test
	fun `student timetable resolves child name from persisted user data`() {
		assertEquals(
			"Ada Lovelace",
			ElementDisplayName.longName(PeriodElement(ElementType.STUDENT, 200), userData)
		)
	}

	@Test
	fun `blank long names fall back to the stored name instead of a sentinel`() {
		assertEquals(
			"11B",
			ElementDisplayName.longName(PeriodElement(ElementType.CLASS, 11), userData)
		)
		assertEquals(
			"XY",
			ElementDisplayName.longName(PeriodElement(ElementType.TEACHER, 21), userData)
		)
	}

	@Test
	fun `unknown element has no local name`() {
		assertNull(ElementDisplayName.longName(PeriodElement(ElementType.CLASS, 999), userData))
		assertNull(ElementDisplayName.longName(PeriodElement(ElementType.CLASS, 10), null))
	}
}
