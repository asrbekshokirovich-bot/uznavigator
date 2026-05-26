import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.uznavigator.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uznavigator.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        val mapboxPublicToken = localProps.getProperty("MAPBOX_PUBLIC_TOKEN", "")
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxPublicToken\"")
        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")

        // Inject Mapbox token into AndroidManifest <meta-data> tag
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = mapboxPublicToken

        // CRITICAL: Maps SDK 10.x reads token from R.string.mapbox_access_token
        // Inject it as a string resource at build time (kept out of git via local.properties)
        resValue("string", "mapbox_access_token", mapboxPublicToken)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ── Mapbox ───────────────────────────────────────────────────────────────
    implementation("com.mapbox.maps:android:10.18.3")
    implementation("com.mapbox.navigation:android:2.20.0")
    implementation("com.mapbox.navigation:ui-maps:2.20.0")

    // ── Android Auto ─────────────────────────────────────────────────────────
    implementation("androidx.car.app:app:1.7.0")

    // ── Supabase ──────────────────────────────────────────────────────────────
    // (intentionally disabled — saved-places sync is not wired into UI yet.
    //  To enable later, restore these and re-add SupabaseClient.kt + SavedPlace.kt)
    // implementation(platform("io.github.jan-tennert.supabase:bom:2.5.4"))
    // implementation("io.github.jan-tennert.supabase:postgrest-kt")
    // implementation("io.github.jan-tennert.supabase:auth-kt")
    // implementation("io.ktor:ktor-client-android:2.3.12")

    // ── Location ──────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ── Networking + JSON ─────────────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Core Android ──────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
