package com.deelabs.untisplus.modules

import com.deelabs.untisplus.workers.AutoMuteSetupWorker
import com.deelabs.untisplus.workers.DailyWorker
import com.deelabs.untisplus.workers.EventReminderSyncWorker
import com.deelabs.untisplus.workers.EventReminderWorker
import com.deelabs.untisplus.workers.ImportedCalendarSyncWorker
import com.deelabs.untisplus.workers.NotificationSetupWorker
import com.deelabs.untisplus.workers.TimetableChangeWorker
import com.deelabs.untisplus.workers.WidgetUpdateWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
	workerOf(::DailyWorker)
	workerOf(::WidgetUpdateWorker)
	workerOf(::AutoMuteSetupWorker)
	workerOf(::NotificationSetupWorker)
	workerOf(::ImportedCalendarSyncWorker)
	workerOf(::TimetableChangeWorker)
	workerOf(::EventReminderSyncWorker)
	workerOf(::EventReminderWorker)
}
