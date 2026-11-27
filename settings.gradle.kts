rootProject.name = "UntisPlus"

pluginManagement {
	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
	}
}

include(":app")
include(":material-color-utils")
include(":api")
include(":shared")
