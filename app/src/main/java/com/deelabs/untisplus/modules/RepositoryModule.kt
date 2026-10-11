package com.deelabs.untisplus.modules

import com.deelabs.untisplus.data.repository.GlobalSettingsRepository
import com.deelabs.untisplus.data.repository.ImportedCalendarRepository
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.MessagesRepository
import com.deelabs.untisplus.data.repository.ProxyRepository
import com.deelabs.untisplus.data.repository.TimetableChangeBus
import com.deelabs.untisplus.data.repository.TimetableRepository
import com.deelabs.untisplus.data.repository.UntisInfoCenterRepository
import com.deelabs.untisplus.data.repository.UntisMasterDataRepository
import com.deelabs.untisplus.data.repository.UntisMessagesRepository
import com.deelabs.untisplus.data.repository.UntisTimetableRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.data.repository.UserSettingsRepository
import com.deelabs.untisplus.mappers.TimetableMapper
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule = module {
	single<UserRepository> { UserRepository(get(), get(), get()) }

	single<ImportedCalendarRepository> {
		ImportedCalendarRepository(get(), get(), get(named("ics")), androidContext())
	}

	single {
		ProxyRepository(get(), get(), get(), get(), get(), get(), get(), get())
	}

	single { TimetableChangeBus() }

	factory<UserSettingsRepository> { UserSettingsRepository(get(), get()) }

	factory { GlobalSettingsRepository(get()) }

	factory<MasterDataRepository> { UntisMasterDataRepository(get(), get()) }

	factory<TimetableRepository> {
		UntisTimetableRepository(get(), get(), get(named("cacheDir")), get(), get())
	}

	factory<InfoCenterRepository> {
		UntisInfoCenterRepository(
			get(), get(), get(), get(), get(), get(named("cacheDir")), get()
		)
	}

	factory<MessagesRepository> {
		UntisMessagesRepository(get(), get(named("cacheDir")), get())
	}

	factory { TimetableMapper(get(), get(), get(), get()) }
}
