package com.deelabs.untisplus.modules

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.deelabs.untisplus.services.AutoMuteService
import com.deelabs.untisplus.services.AutoMuteServiceInterruptionFilterImpl
import com.deelabs.untisplus.services.AutoMuteServiceRingerModeImpl
import com.deelabs.untisplus.services.AutoMuteServiceZenRuleImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val autoMuteModule = module {
	factory<AutoMuteService> {
		val context = androidContext()
		val notificationManager =
			context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			AutoMuteServiceZenRuleImpl(context, notificationManager!!)
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			AutoMuteServiceInterruptionFilterImpl(notificationManager!!)
		} else {
			AutoMuteServiceRingerModeImpl(
				context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
			)
		}
	}
}
