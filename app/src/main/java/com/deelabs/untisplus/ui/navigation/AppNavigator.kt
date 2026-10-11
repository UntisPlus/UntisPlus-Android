package com.deelabs.untisplus.ui.navigation

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class NavigationAction <T: Any> (
	val destination: T,
	val navOptions:NavOptionsBuilder.() -> Unit = {},
)

class AppNavigator constructor() {
	private val _navActions = Channel<NavigationAction<*>?>(Channel.UNLIMITED)
	val navActions = _navActions.receiveAsFlow()

	fun <T: Any> navigate(action: NavigationAction<T>) {
		_navActions.trySend(action)
	}

	fun <T: Any> navigate(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
		_navActions.trySend(NavigationAction(route, builder))
	}

	fun popBackStack() {
		_navActions.trySend(null)
	}
}
