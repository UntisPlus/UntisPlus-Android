package com.deelabs.untisplus.data.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.deelabs.untisplus.api.model.untis.SchoolInfo
import com.deelabs.untisplus.api.model.untis.Settings
import com.deelabs.untisplus.api.model.untis.UserData
import com.deelabs.untisplus.api.model.untis.masterdata.TimeGrid
import com.deelabs.untisplus.data.database.entities.AbsenceReasonEntity
import com.deelabs.untisplus.data.database.entities.DepartmentEntity
import com.deelabs.untisplus.data.database.entities.DutyEntity
import com.deelabs.untisplus.data.database.entities.EventReasonEntity
import com.deelabs.untisplus.data.database.entities.EventReasonGroupEntity
import com.deelabs.untisplus.data.database.entities.ExcuseStatusEntity
import com.deelabs.untisplus.data.database.entities.HolidayEntity
import com.deelabs.untisplus.data.database.entities.ImportedCalendarDao
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEntity
import com.deelabs.untisplus.data.database.entities.ImportedCalendarEventEntity
import com.deelabs.untisplus.data.database.entities.KlasseEntity
import com.deelabs.untisplus.data.database.entities.RoomEntity
import com.deelabs.untisplus.data.database.entities.SchoolYearEntity
import com.deelabs.untisplus.data.database.entities.SubjectEntity
import com.deelabs.untisplus.data.database.entities.TeacherEntity
import com.deelabs.untisplus.data.database.entities.TeachingMethodEntity
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json

@Database(
	version = 14,
	entities = [
		User::class,
		AbsenceReasonEntity::class,
		DepartmentEntity::class,
		DutyEntity::class,
		EventReasonEntity::class,
		EventReasonGroupEntity::class,
		ExcuseStatusEntity::class,
		HolidayEntity::class,
		ImportedCalendarEntity::class,
		ImportedCalendarEventEntity::class,
		KlasseEntity::class,
		RoomEntity::class,
		SubjectEntity::class,
		TeacherEntity::class,
		TeachingMethodEntity::class,
		SchoolYearEntity::class,
	],
	autoMigrations = [
		AutoMigration(from = 8, to = 9),
		AutoMigration(from = 9, to = 10),
		AutoMigration(from = 10, to = 11, spec = MigrationSpec10to11::class),
		AutoMigration(from = 11, to = 12, spec = MigrationSpec11to12::class),
		AutoMigration(from = 12, to = 13),
		AutoMigration(from = 13, to = 14)
	]
)
@TypeConverters(UserConverters::class)
@ConstructedBy(UserDatabaseConstructor::class)
abstract class UserDatabase : RoomDatabase() {
	abstract fun userDao(): UserDao
	abstract fun importedCalendarDao(): ImportedCalendarDao
}

// The Room compiler generates the `actual` implementations for each target.
@Suppress("KotlinNoActualForExpect")
expect object UserDatabaseConstructor : RoomDatabaseConstructor<UserDatabase> {
	override fun initialize(): UserDatabase
}

internal class UserConverters {
	private inline fun <reified T> encode(value: T?): String? =
		value?.let { Json.encodeToString<T>(it) }

	private inline fun <reified T> decode(string: String?): T? =
		string?.let { Json.decodeFromString<T>(it) }

	@TypeConverter
	fun encodeTimeGrid(timeGrid: TimeGrid?): String? = encode(timeGrid)

	@TypeConverter
	fun decodeTimeGrid(string: String?): TimeGrid? = decode(string)

	@TypeConverter
	fun encodeUntisUserData(untisUserData: UserData?): String? = encode(untisUserData)

	@TypeConverter
	fun decodeUntisUserData(string: String?): UserData? = decode(string)

	@TypeConverter
	fun encodeUntisSettings(untisSettings: Settings?): String? = encode(untisSettings)

	@TypeConverter
	fun decodeUntisSettings(string: String?): Settings? = decode(string)

	@TypeConverter
	fun encodeUntisSchoolInfo(untisSchoolInfo: SchoolInfo?): String? = encode(untisSchoolInfo)

	@TypeConverter
	fun decodeUntisSchoolInfo(string: String?): SchoolInfo? = decode(string)

	@TypeConverter
	fun encodeLongList(intList: List<Long>?): String? = encode(intList)

	@TypeConverter
	fun decodeLongList(string: String?): List<Long>? = decode(string)

	@TypeConverter
	fun encodeDate(date: LocalDate?): String? = date?.toString()

	@TypeConverter
	fun decodeDate(date: String?): LocalDate? =
		date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

	@TypeConverter
	fun encodeTime(date: LocalTime?): String? = date?.toString()

	@TypeConverter
	fun decodeTime(date: String?): LocalTime? =
		date?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}
