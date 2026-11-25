package com.deelabs.untisplus.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn.Entries(
	DeleteColumn(
		tableName = "User",
		columnName = "bookmarks"
	)
)
class MigrationSpec10to11 : AutoMigrationSpec

@RenameColumn(
	tableName = "User",
	fromColumnName = "apiUrl",
	toColumnName = "apiHost"
)
class MigrationSpec11to12 : AutoMigrationSpec
