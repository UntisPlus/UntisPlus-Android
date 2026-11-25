package com.deelabs.untisplus.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Session-wide child selection shared by the timetable and Info Center. */
interface ChildSelectionRepository {
	val selectedChildId: StateFlow<Long?>
	fun selectChild(id: Long?)
}

class DefaultChildSelectionRepository : ChildSelectionRepository {
	private val _selectedChildId = MutableStateFlow<Long?>(null)
	override val selectedChildId: StateFlow<Long?> = _selectedChildId

	override fun selectChild(id: Long?) {
		_selectedChildId.value = id
	}
}
