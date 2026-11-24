package com.deelabs.untisplus.helpers

/**
 * Minimal semver comparison for UntisPlus release tags. A stable release with
 * the same major/minor/patch is newer than any beta of that version.
 */
data class AppVersion(
	val major: Int,
	val minor: Int,
	val patch: Int,
	val beta: Int? = null,
) : Comparable<AppVersion> {
	override fun compareTo(other: AppVersion): Int = compareValuesBy(
		this,
		other,
		{ it.major },
		{ it.minor },
		{ it.patch },
		{ it.beta ?: Int.MAX_VALUE }
	)

	companion object {
		private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:[-.]beta\.?(\d+))?""")

		fun parse(value: String): AppVersion? {
			val match = PATTERN.matchEntire(value.trim()) ?: return null
			return AppVersion(
				major = match.groupValues[1].toInt(),
				minor = match.groupValues[2].toInt(),
				patch = match.groupValues[3].toInt(),
				beta = match.groupValues[4].takeIf { it.isNotBlank() }?.toInt()
			)
		}

		fun isNewer(remote: String, current: String): Boolean {
			val remoteVersion = parse(remote) ?: return false
			val currentVersion = parse(current) ?: return false
			return remoteVersion > currentVersion
		}
	}
}
