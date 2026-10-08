plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The Glyph Matrix SDK may not be redistributed (Nothing EULA §2.1(c)), so it is
// never committed. When a developer has downloaded it into app/libs/, the real
// Glyph integration is compiled; otherwise a stub that reports "unavailable" is.
val glyphAar = file("libs/glyph-matrix-sdk-2.0.aar")
val hasGlyphSdk = glyphAar.exists()
logger.lifecycle("Dot Habits: Glyph Matrix SDK ${if (hasGlyphSdk) "found" else "NOT found – building Glyph stub"}")

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "HAS_GLYPH_SDK", hasGlyphSdk.toString())
        resValue("bool", "glyph_toy_enabled", hasGlyphSdk.toString())
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
        resValues = true
    }

    sourceSets {
        getByName("main") {
            java.directories.add(if (hasGlyphSdk) "src/glyph/java" else "src/glyphStub/java")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    if (hasGlyphSdk) implementation(files(glyphAar))

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
