package com.deelabs.untisplus.modules

import com.deelabs.untisplus.services.WeekLogicService
import com.deelabs.untisplus.services.WeekLogicServiceImpl
import org.koin.dsl.module

val weekLogicModule = module {
	factory<WeekLogicService> { WeekLogicServiceImpl(get()) }
}
