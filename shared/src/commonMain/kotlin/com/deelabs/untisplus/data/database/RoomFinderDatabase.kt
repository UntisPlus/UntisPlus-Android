package com.deelabs.untisplus.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.deelabs.untisplus.data.database.entities.RoomFinderDao
import com.deelabs.untisplus.data.database.entities.RoomFinderEntity

@Database(
	version = 1,
	entities = [
		RoomFinderEntity::class,
	]
)
@TypeConverters(RoomFinderConverters::class)
@ConstructedBy(RoomFinderDatabaseConstructor::class)
abstract class RoomFinderDatabase : RoomDatabase() {
	abstract fun roomFinderDao(): RoomFinderDao
}

// The Room compiler generates the `actual` implementations for each target.
@Suppress("KotlinNoActualForExpect")
expect object RoomFinderDatabaseConstructor : RoomDatabaseConstructor<RoomFinderDatabase> {
	override fun initialize(): RoomFinderDatabase
}

internal class RoomFinderConverters {
	@TypeConverter
	fun encodeBooleanList(list: List<Boolean>?): String? = list?.joinToString("") { if (it) "1" else "0" }

	@TypeConverter
	fun decodeBooleanList(string: String?): List<Boolean>? = string?.toCharArray()?.map { it == '1' }
}
