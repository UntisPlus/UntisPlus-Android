package com.deelabs.untisplus.modules

import androidx.compose.material3.ColorScheme
import androidx.lifecycle.SavedStateHandle
import com.deelabs.untisplus.ui.pages.infocenter.InfoCenterViewModel
import com.deelabs.untisplus.ui.pages.login.LoginViewModel
import com.deelabs.untisplus.ui.pages.login.datainput.LoginDataInputViewModel
import com.deelabs.untisplus.ui.pages.login.schoolsearch.SchoolSearchViewModel
import com.deelabs.untisplus.ui.pages.login.sso.SsoLoginViewModel
import com.deelabs.untisplus.ui.pages.main.MainViewModel
import com.deelabs.untisplus.ui.pages.roomfinder.RoomFinderViewModel
import com.deelabs.untisplus.ui.pages.settings.CalendarSubscriptionsViewModel
import com.deelabs.untisplus.ui.pages.settings.ChangeSubscriptionsViewModel
import com.deelabs.untisplus.ui.pages.settings.ImportedCalendarsViewModel
import com.deelabs.untisplus.ui.pages.settings.SettingsScreenViewModel
import com.deelabs.untisplus.ui.pages.settings.automute.AutoMuteSettingsViewModel
import com.deelabs.untisplus.ui.pages.timetable.TimetableDrawerViewModel
import com.deelabs.untisplus.ui.pages.timetable.TimetableViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val viewModelModule = module {
	viewModelOf(::MainViewModel)

	viewModel { params ->
		TimetableViewModel(
			navigator = get(),
			userSettingsRepository = get(),
			timetableMapper = get(),
			importedCalendarRepository = get(),
			userRepository = get(),
			timetableRepository = get(),
			masterDataRepository = get(),
			timetableChangeBus = get(),
			clock = get(),
			weekLogicService = get(),
			buildConfigFieldsProvider = get(),
			savedStateHandle = params.get<SavedStateHandle>(),
		)
	}

	viewModelOf(::RoomFinderViewModel)

	viewModelOf(::TimetableDrawerViewModel)

	viewModelOf(::ImportedCalendarsViewModel)

	viewModelOf(::CalendarSubscriptionsViewModel)

	viewModelOf(::ChangeSubscriptionsViewModel)

	viewModel { params ->
		SsoLoginViewModel(
			navigator = get(),
			savedStateHandle = params.get<SavedStateHandle>(),
		)
	}

	viewModelOf(::LoginViewModel)

	viewModel { params ->
		LoginDataInputViewModel(
			userDao = get(),
			schoolSearchApi = get(),
			userDataApi = get(),
			timetableRepository = get(),
			weekLogicService = get(),
			navigator = get(),
			userRepository = get(),
			codeScanService = get(),
			cacheDir = get(named("cacheDir")),
			savedStateHandle = params.get<SavedStateHandle>(),
		)
	}

	viewModelOf(::SchoolSearchViewModel)

	viewModel { params ->
		AutoMuteSettingsViewModel(
			userDao = get(),
			userSettingsRepository = get(),
			autoMuteService = get(),
			savedStateHandle = params.get<SavedStateHandle>(),
		)
	}

	viewModelOf(::InfoCenterViewModel)

	viewModel { params ->
		SettingsScreenViewModel(
			globalSettingsRepository = get(),
			userRepository = get(),
			userSettingsRepository = get(),
			autoMuteService = get(),
			savedStateHandle = params.get<SavedStateHandle>(),
			masterDataRepository = get(),
			httpClient = get(named("json")),
			colorScheme = params.get<ColorScheme>(),
			proxyRepository = get(),
		)
	}
}
