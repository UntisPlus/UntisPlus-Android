package com.deelabs.untisplus.ui.pages.infocenter.fragments

import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.Person

/** "All" plus one tab per child, shared by the Events and Absences pages. */
@Composable
fun InfoCenterChildTabs(
	children: List<Person>,
	selectedChildId: Long?,
	onSelectChild: (Long?) -> Unit
) {
	if (children.isEmpty()) return

	val childIndex = children.indexOfFirst { it.id == selectedChildId }
	val selectedTabIndex = if (selectedChildId == null) 0 else childIndex + 1

	ScrollableTabRow(
		selectedTabIndex = selectedTabIndex.coerceAtLeast(0),
		edgePadding = 0.dp
	) {
		Tab(
			selected = selectedChildId == null,
			onClick = { onSelectChild(null) },
			text = { Text(stringResource(R.string.parent_all_children)) }
		)
		children.forEach { child ->
			Tab(
				selected = child.id == selectedChildId,
				onClick = { onSelectChild(child.id) },
				text = { Text(child.fullName()) }
			)
		}
	}
}
