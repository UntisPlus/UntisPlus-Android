package com.deelabs.untisplus.modules

import androidx.room.Room
import com.deelabs.untisplus.data.database.MIGRATIONS_USER_LEGACY
import com.deelabs.untisplus.data.database.MIGRATION_USER_7_8
import com.deelabs.untisplus.data.database.RoomFinderDatabase
import com.deelabs.untisplus.data.database.UserDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
	single {
		Room.databaseBuilder(
			androidContext(),
			UserDatabase::class.java, "userdata.db"
		)
			.addMigrations(
				*MIGRATIONS_USER_LEGACY.toTypedArray(),
				MIGRATION_USER_7_8,
			)
			.build()
	}

	single { get<UserDatabase>().userDao() }

	single { get<UserDatabase>().importedCalendarDao() }

	single {
		Room.databaseBuilder(
			androidContext(),
			RoomFinderDatabase::class.java, "roomfinder.db"
		)
			// In old versions, each profile has their own roomfinder database file.
			// This makes it hard to run an automatic migration.
			// Since the data can be recovered easily, migration is skipped and a new database is used.
			.build()
	}

	single { get<RoomFinderDatabase>().roomFinderDao() }
}
