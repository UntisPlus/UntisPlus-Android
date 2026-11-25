package com.deelabs.untisplus.activities

import android.app.Application
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deelabs.untisplus.modules.appModules
import com.deelabs.untisplus.workers.DailyWorker
import com.deelabs.untisplus.workers.DailyWorker.Companion.TAG_DAILY_WORK
import com.deelabs.untisplus.workers.ImportedCalendarSyncWorker
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class App : Application() {
	@OptIn(DelicateCoroutinesApi::class)
	override fun onCreate() {
		super.onCreate()

		startKoin {
			androidContext(this@App)
			workManagerFactory()
			modules(appModules)
		}

		GlobalScope.launch {
			WorkManager.getInstance(applicationContext).apply {
				cancelAllWorkByTag(TAG_DAILY_WORK)
				enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())
			}
		}

		ImportedCalendarSyncWorker.schedule(applicationContext)
	}
}
