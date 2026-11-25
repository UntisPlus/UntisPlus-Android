package com.deelabs.untisplus.modules

import com.deelabs.untisplus.domain.GetAbsencesUseCase
import com.deelabs.untisplus.domain.GetCurrentSchoolYearUseCase
import com.deelabs.untisplus.domain.GetExamsUseCase
import com.deelabs.untisplus.domain.GetHomeworkUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCase
import com.deelabs.untisplus.domain.GetMessagesOfDayUseCaseImpl
import com.deelabs.untisplus.domain.GetMessagesUseCase
import com.deelabs.untisplus.domain.GetMessagesUseCaseImpl
import com.deelabs.untisplus.domain.GetOfficeHourRegistrationsUseCase
import com.deelabs.untisplus.domain.GetOfficeHoursUseCase
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCase
import com.deelabs.untisplus.domain.GetRoomFinderItemsUseCaseImpl
import org.koin.dsl.module

val useCaseModule = module {
	factory<GetMessagesOfDayUseCase> { GetMessagesOfDayUseCaseImpl(get(), get()) }

	factory<GetMessagesUseCase> { GetMessagesUseCaseImpl(get(), get()) }

	factory<GetRoomFinderItemsUseCase> {
		GetRoomFinderItemsUseCaseImpl(get(), get(), get(), get(), get())
	}

	factory { GetCurrentSchoolYearUseCase(get()) }

	factory { GetAbsencesUseCase(get(), get(), get(), get()) }

	factory { GetExamsUseCase(get(), get(), get()) }

	factory { GetHomeworkUseCase(get(), get(), get()) }

	factory { GetOfficeHoursUseCase(get(), get()) }

	factory { GetOfficeHourRegistrationsUseCase(get(), get()) }
}
