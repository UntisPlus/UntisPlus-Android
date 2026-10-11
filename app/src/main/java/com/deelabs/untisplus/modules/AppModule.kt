package com.deelabs.untisplus.modules

import org.koin.core.module.Module

val appModules: List<Module> = listOf(
	configurationModule,
	databaseModule,
	dataStoreModule,
	jsonRpcApiClientModule,
	restApiClientModule,
	autoMuteModule,
	codeScanModule,
	repositoryModule,
	useCaseModule,
	weekLogicModule,
	navigationModule,
	viewModelModule,
	workerModule
)
