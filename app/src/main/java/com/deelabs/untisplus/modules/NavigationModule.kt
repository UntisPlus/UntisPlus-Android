package com.deelabs.untisplus.modules

import com.deelabs.untisplus.ui.navigation.AppNavigator
import org.koin.dsl.module

val navigationModule = module {
	single { AppNavigator() }
}
