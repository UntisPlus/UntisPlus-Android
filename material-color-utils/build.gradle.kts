import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Vendored Kotlin port of material-color-utilities (Apache-2.0). Converted from
// the Java sources so it can be shared with the iOS app (docs/KMP-MIGRATION.md).
plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.agp.library)
}

kotlin {
	androidTarget {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	iosArm64()
	iosSimulatorArm64()
	iosX64()

	jvmToolchain(21)

	sourceSets {
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
			}
		}
	}
}

android {
	namespace = "com.deelabs.untisplus.ui.material"
	compileSdk = 35

	defaultConfig {
		minSdk = 21
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}
