package com.deelabs.untisplus.modules

import com.deelabs.untisplus.helpers.ApplicationBuildConfigFieldsProvider
import com.deelabs.untisplus.helpers.BuildConfigFieldsProvider
import crocodile8.universal_cache.time.SystemTimeProvider
import crocodile8.universal_cache.time.TimeProvider
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.time.Clock

val configurationModule = module {
	single<Path>(named("cacheDir")) { androidContext().cacheDir.toOkioPath() }

	single { Clock.systemDefaultZone() }

	single<TimeProvider> { SystemTimeProvider }

	single<BuildConfigFieldsProvider> { ApplicationBuildConfigFieldsProvider() }
}
