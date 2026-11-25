package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.UserWithData

/**
 * Resolves element names from the complete locally persisted master data.
 *
 * The active-only maps in [UntisMasterDataRepository] back the element picker.
 * They must not be used for display names: bookmarks, shortcuts and lesson
 * targets can reference elements that are no longer flagged as active, and a
 * lookup in those maps would fall back to the `?` sentinel instead of the
 * element's real name.
 */
internal object ElementDisplayName {
	fun shortName(id: Long, type: ElementType?, userData: UserWithData?): String? = when (type) {
		ElementType.STUDENT -> userData?.user?.userData?.children?.firstOrNull { it?.id == id }?.fullName()
		ElementType.CLASS -> userData?.klassen?.firstOrNull { it.id == id }?.name
		ElementType.TEACHER -> userData?.teachers?.firstOrNull { it.id == id }?.name
		ElementType.SUBJECT -> userData?.subjects?.firstOrNull { it.id == id }?.name
		ElementType.ROOM -> userData?.rooms?.firstOrNull { it.id == id }?.name
		else -> null
	}?.takeIf(String::isNotBlank)

	fun longName(id: Long, type: ElementType, userData: UserWithData?): String? {
		val fullName = when (type) {
			ElementType.STUDENT -> userData?.user?.userData?.children?.firstOrNull { it?.id == id }?.fullName()
			ElementType.CLASS -> userData?.klassen?.firstOrNull { it.id == id }?.longName
			ElementType.TEACHER -> userData?.teachers?.firstOrNull { it.id == id }
				?.let { listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ") }
			ElementType.SUBJECT -> userData?.subjects?.firstOrNull { it.id == id }?.longName
			ElementType.ROOM -> userData?.rooms?.firstOrNull { it.id == id }?.longName
			else -> null
		}?.takeIf(String::isNotBlank)

		// Some schools only store the short name for an element. It is still the
		// element's real name, so prefer it over the "?" sentinel.
		return fullName ?: shortName(id, type, userData)
	}

	fun shortName(element: PeriodElement, userData: UserWithData?): String? =
		shortName(element.id, element.type, userData)

	fun longName(element: PeriodElement, userData: UserWithData?): String? =
		longName(element.id, element.type, userData)
}
