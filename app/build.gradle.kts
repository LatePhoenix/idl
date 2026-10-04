import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Supabase project settings come from local.properties (never committed):
//   supabase.url=https://<ref>.supabase.co
//   supabase.anonKey=<anon public key>
// When absent the app runs on the in-process FakeIdlBackend.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String) = (localProps.getProperty(key) ?: System.getenv(key.uppercase().replace('.', '_')) ?: "")
    .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "app.idl"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.idl"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("supabase.anonKey")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // PrivacyVectorsTest: -Pidl.updateGolden=true regenerates contract/privacy_vectors.json.
            it.systemProperty("idl.updateGolden", project.findProperty("idl.updateGolden") ?: "false")
            it.inputs.files(rootProject.file("contract"))
            // SupabaseRestIT runs only when PostgREST from supabase/tests/run.sh --rest is up.
            it.systemProperty("idl.postgrestUrl", project.findProperty("idl.postgrestUrl") ?: "")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
    outputDir.set(file("src/test/snapshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.roborazzi)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Espresso >= 3.7 is required on API 36+ (older builds reflect on removed InputManager APIs).
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
