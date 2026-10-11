import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// In-repo home for code shared with the iOS app. This is the seed of the
// `UntisPlus-Shared` repository described in docs/KMP-MIGRATION.md; modules are
// converted here and extracted once they are free of JVM-only dependencies.
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
}

android {
	namespace = "com.deelabs.untisplus.shared"
	compileSdk = 35

	defaultConfig {
		minSdk = 21
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}
