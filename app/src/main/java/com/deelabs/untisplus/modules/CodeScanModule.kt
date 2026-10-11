package com.deelabs.untisplus.modules

import com.deelabs.untisplus.services.CodeScanService
import com.deelabs.untisplus.services.CodeScanServiceImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val codeScanModule = module {
	factory<CodeScanService> { CodeScanServiceImpl(androidContext()) }
}
