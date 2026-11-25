import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.util.Locale

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.agp.library)
	alias(libs.plugins.openapi.generator)
	alias(libs.plugins.kotlin.serialization)
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

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.serialization.json)
			api(libs.kotlinx.datetime)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.serialization)
		}
		jvmTest.dependencies {
			implementation(libs.junit)
			implementation(libs.ktor.client.mock)
		}
	}

	jvmToolchain(21)
}

android {
	namespace = "com.deelabs.untisplus.api"
	compileSdk = 35

	defaultConfig {
		minSdk = 21
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

// Build Untis Internal OpenAPI specs. Each spec gets its own output directory:
// sharing one directory makes the generators invalidate each other on every build.
val generatedRoot = "${layout.buildDirectory.get()}/openapi"
val generatedSrcDirs = mutableListOf<String>()
val generateTasks = mutableListOf<TaskProvider<GenerateTask>>()

val apiSpecList = mutableListOf<File>()
val dir = file("${layout.projectDirectory}/spec/untis-intern/")
dir.walk().filter { it.isFile && Regex("""untis-.*\.yaml""").matches(it.name) }.sortedBy { it.name }.forEach { file ->
	apiSpecList.add(file)
}
apiSpecList.forEachIndexed { index, file ->
	val apiName = file.name.replace(".yaml", "").replace("untis-", "")
	val taskName = "Untis" + apiName.split('-').joinToString("") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
	val packageName = apiName.replace("-", "_")
	val generatedDir = "$generatedRoot/$apiName"

	val generateTask = tasks.register("openApiGenerate$taskName", GenerateTask::class) {
		generatorName.set("kotlin")
		inputSpec.set("${layout.projectDirectory}/spec/untis-intern/untis-$apiName.yaml")
		outputDir.set(generatedDir)
		apiPackage.set("com.deelabs.untisplus.api.$packageName")
		modelPackage.set("com.deelabs.untisplus.model.$packageName")
		// Only the first spec emits the shared org.openapitools.client support code.
		// Later specs would otherwise produce duplicate classes once all output
		// directories are added to commonMain.
		if (index > 0) {
			globalProperties.set(
				mapOf(
					"apis" to "",
					"models" to "",
					"supportingFiles" to "false"
				)
			)
		}
		configOptions.set(
			mapOf(
				"library" to "jvm-ktor",
				"dateLibrary" to "java8",
				"serializationLibrary" to "kotlinx_serialization"
			)
		)
		// Replace the JVM-only types the Kotlin generator defaults to with
		// multiplatform equivalents so the generated code can live in commonMain.
		typeMappings.set(
			mapOf(
				"java.time.OffsetDateTime" to "com.deelabs.untisplus.api.serializer.DateTime",
				"java.time.LocalDateTime" to "com.deelabs.untisplus.api.serializer.DateTime",
				"java.time.LocalDate" to "com.deelabs.untisplus.api.serializer.Date",
				"java.math.BigDecimal" to "kotlin.Double",
				"java.io.File" to "kotlin.ByteArray",
				"java.net.URI" to "kotlin.String",
				"java.util.UUID" to "kotlin.String"
			)
		)
	}
	generateTasks.add(generateTask)
	generatedSrcDirs.add("$generatedDir/src/main/kotlin")
}

kotlin.sourceSets.named("commonMain") {
	generatedSrcDirs.forEach { srcDir ->
		kotlin.srcDir(srcDir)
	}
}

// Make every Kotlin compilation wait for the generated sources.
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
	dependsOn(generateTasks)
}
