import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// In-repo home for code shared with the iOS app. This is the seed of the
// `UntisPlus-Shared` repository described in docs/KMP-MIGRATION.md; modules are
// converted here and extracted once they are free of JVM-only dependencies.
plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.agp.library)
	alias(libs.plugins.ksp)
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
		commonMain.dependencies {
			api(project(":api"))
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.androidx.room.runtime)
		}
	}
}

ksp {
	arg("room.schemaLocation", "$projectDir/schemas")
}

// The JVM target pins bytecode to 17 while the toolchain is 21; keep the
// Java compilation tasks (including the one KSP creates) in sync.
tasks.withType<JavaCompile>().configureEach {
	sourceCompatibility = JavaVersion.VERSION_17.toString()
	targetCompatibility = JavaVersion.VERSION_17.toString()
}

dependencies {
	add("kspAndroid", libs.androidx.room.compiler)
	add("kspJvm", libs.androidx.room.compiler)
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
