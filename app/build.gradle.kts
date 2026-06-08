import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

val localProps =
    Properties().also { props ->
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { props.load(it) }
    }

android {
    namespace = "com.whatstats"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whatstats"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "1.5.1"
        val admobAppId =
            project
                .findProperty("ADMOB_APP_ID")
                ?.toString()
                ?: localProps.getProperty("ADMOB_APP_ID")
                ?: "ca-app-pub-3940256099942544~3347511713"
        val admobBannerUnitId =
            project
                .findProperty("ADMOB_BANNER_UNIT_ID")
                ?.toString()
                ?: localProps.getProperty("ADMOB_BANNER_UNIT_ID")
                ?: "ca-app-pub-3940256099942544/9214589741"
        val admobNativeUnitId =
            project
                .findProperty("ADMOB_NATIVE_UNIT_ID")
                ?.toString()
                ?: localProps.getProperty("ADMOB_NATIVE_UNIT_ID")
                ?: "ca-app-pub-3940256099942544/2247696110"
        val admobInterstitialUnitId =
            project
                .findProperty("ADMOB_INTERSTITIAL_UNIT_ID")
                ?.toString()
                ?: localProps.getProperty("ADMOB_INTERSTITIAL_UNIT_ID")
                ?: "ca-app-pub-3940256099942544/1033173712"
        val admobRewardedInterstitialUnitId =
            project
                .findProperty("ADMOB_REWARDED_INTERSTITIAL_UNIT_ID")
                ?.toString()
                ?: localProps.getProperty("ADMOB_REWARDED_INTERSTITIAL_UNIT_ID")
                ?: "ca-app-pub-3940256099942544/5354046379"
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"${admobAppId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"${admobBannerUnitId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"${admobNativeUnitId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"${admobInterstitialUnitId.replace("\"", "\\\"")}\"")
        buildConfigField(
            "String",
            "ADMOB_REWARDED_INTERSTITIAL_UNIT_ID",
            "\"${admobRewardedInterstitialUnitId.replace("\"", "\\\"")}\"",
        )
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("STORE_FILE") ?: localProps.getProperty("STORE_FILE") ?: "")
            storePassword = (project.findProperty("STORE_PASSWORD") ?: localProps.getProperty("STORE_PASSWORD") ?: "").toString()
            keyAlias = (project.findProperty("KEY_ALIAS") ?: localProps.getProperty("KEY_ALIAS") ?: "").toString()
            keyPassword = (project.findProperty("KEY_PASSWORD") ?: localProps.getProperty("KEY_PASSWORD") ?: "").toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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

    androidResources {
        localeFilters += listOf("en", "it", "de", "es", "fr", "hi", "pt-rBR", "tr")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Core Android ─────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // ── Root Access (libsu) ──────────────────────────────────────
    val libsuVersion = "5.3.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:io:$libsuVersion")

    // ── Serialization ────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Coroutines ───────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Hilt DI ──────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Room (lettura db decifrato) ──────────────────────────────
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // ── Grafici (Vico — Compose-native) ──────────────────────────
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")

    // ── DataStore (preferenze) ───────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // ── Coil (image loading) ───────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // ── Accompanist ──────────────────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // ── Play Billing (Pro unlock) ────────────────────────────────
    // Rimosso: app gratuita senza acquisti in-app

    // ── AdMob + UMP ───────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // ── Test ─────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
