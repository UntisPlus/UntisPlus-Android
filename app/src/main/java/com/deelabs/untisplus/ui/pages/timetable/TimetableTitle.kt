package com.deelabs.untisplus.ui.pages.timetable

import com.deelabs.untisplus.api.model.untis.enumeration.ElementType
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.repository.MasterDataRepository

/**
 * Resolves the top timetable title from locally available data.
 *
 * A requested element means the user explicitly picked another timetable, so
 * that element's full name must win over the signed-in profile name. The
 * profile name is only used for the personal timetable (or when no element was
 * requested at all), matching v1.2.0. Name lookup itself is handled by
 * [MasterDataRepository], which resolves inactive elements and blank long
 * names instead of returning the `?` sentinel for a known element.
 */
internal fun timetableTitle(
	requestedElement: PeriodElement?,
	personalElement: PeriodElement?,
	masterDataRepository: MasterDataRepository,
	profileTitle: String,
	childNames: Map<Long, String> = emptyMap(),
): String {
	val target = requestedElement ?: return profileTitle
	if (target == personalElement) return profileTitle
	if (target.type == ElementType.STUDENT) childNames[target.id]?.let { return it }
	return masterDataRepository.getLongName(target)
}
