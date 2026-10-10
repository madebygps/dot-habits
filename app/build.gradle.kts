import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun gitOutput(vararg args: String): String = providers.exec {
    workingDir(rootDir)
    commandLine("git", *args)
}.standardOutput.asText.get().trim()

val buildCommit = gitOutput("rev-parse", "HEAD")
val buildDirty = gitOutput("status", "--porcelain").isNotEmpty()
val buildTime = Instant.now().toString()
val buildInfo = groovy.json.JsonOutput.toJson(
    mapOf("commit" to buildCommit, "dirty" to buildDirty, "builtAt" to buildTime),
)
abstract class GenerateBuildInfo : DefaultTask() {
    @get:Input
    abstract val metadata: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        outputDirectory.get().file("build-info.json").asFile.apply {
            parentFile.mkdirs()
            writeText(metadata.get())
        }
    }
}

val generateBuildInfo = tasks.register<GenerateBuildInfo>("generateBuildInfo") {
    metadata.set(buildInfo)
    outputDirectory.set(layout.buildDirectory.dir("generated/buildInfo"))
}

android {
    namespace = "com.madebygps.dothabits"
    // Latest AndroidX (Compose 1.12, Navigation 2.10) requires compiling against API 37; the app still targets Android 16 (API 36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.madebygps.dothabits"
        // Built exclusively for Nothing Phone (3) on Nothing OS 4.x (Android 16).
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_COMMIT", "\"$buildCommit\"")
        buildConfigField("boolean", "GIT_DIRTY", buildDirty.toString())
        buildConfigField("String", "BUILT_AT", "\"$buildTime\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateBuildInfo, GenerateBuildInfo::outputDirectory)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Nothing GlyphMatrix SDK (not on Maven). Bundled the way Nothing's official
    // GlyphMatrix-Example-Project does; licence: app/libs/GLYPH_SDK_LICENSE.md.
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.health.connect)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
