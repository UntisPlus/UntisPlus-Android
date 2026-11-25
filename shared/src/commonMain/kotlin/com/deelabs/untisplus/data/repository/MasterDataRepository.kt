package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserWithData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface MasterDataRepository {
	val user: User?
	val userData: UserWithData?

	/** Reads the active user's school years directly, without waiting for the cached snapshot. */
	suspend fun schoolYears(): List<SchoolYearEntity>

	val allClasses: StateFlow<List<PeriodElement>>
	val allTeachers: StateFlow<List<PeriodElement>>
	val allSubjects: StateFlow<List<PeriodElement>>
	val allRooms: StateFlow<List<PeriodElement>>

	fun getShortName(id: Long, type: ElementType? = null, default: String = ELEMENT_NAME_UNKNOWN): String
	fun getShortName(periodElement: PeriodElement, default: String = ELEMENT_NAME_UNKNOWN): String

	fun getLongName(id: Long, type: ElementType, default: String = ELEMENT_NAME_UNKNOWN): String
	fun getLongName(periodElement: PeriodElement, default: String = ELEMENT_NAME_UNKNOWN): String

	fun isAllowed(id: Long, type: ElementType? = null): Boolean
	fun isAllowed(periodElement: PeriodElement): Boolean

	companion object {
		const val ELEMENT_NAME_UNKNOWN = "?"
	}
}

class DefaultMasterDataRepository : MasterDataRepository {
	override val user: User? = null
	override val userData: UserWithData? = null

	override suspend fun schoolYears(): List<SchoolYearEntity> = emptyList()

	override val allClasses: StateFlow<List<PeriodElement>> = MutableStateFlow(emptyList())
	override val allTeachers: StateFlow<List<PeriodElement>> = MutableStateFlow(emptyList())
	override val allSubjects: StateFlow<List<PeriodElement>> = MutableStateFlow(emptyList())
	override val allRooms: StateFlow<List<PeriodElement>> = MutableStateFlow(emptyList())

	override fun getShortName(id: Long, type: ElementType?, default: String): String = "$type:$id"
	override fun getShortName(periodElement: PeriodElement, default: String) = getShortName(periodElement.id, periodElement.type, default)

	override fun getLongName(id: Long, type: ElementType, default: String): String = "$type:$id"
	override fun getLongName(periodElement: PeriodElement, default: String) = getLongName(periodElement.id, periodElement.type)

	override fun isAllowed(id: Long, type: ElementType?): Boolean = true
	override fun isAllowed(periodElement: PeriodElement): Boolean = true
}
