package com.deelabs.untisplus.data.cache

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.createTempDirectory

internal val testFileSystem: FileSystem = FileSystem.SYSTEM

internal fun newTempCacheDir(): Path = createTempDirectory("untisplus-cache-test").toOkioPath()

internal fun Path.deleteTempCacheDir() {
	if (testFileSystem.exists(this)) {
		testFileSystem.deleteRecursively(this)
	}
}

internal fun Path.setTestLastModified(millis: Long) {
	Files.setLastModifiedTime(toNioPath(), FileTime.fromMillis(millis))
}
