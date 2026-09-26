import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val releaseStoreFilePath = System.getenv("RELEASE_STORE_FILE") ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
val hasReleaseSigning = releaseStoreFilePath != null && file(releaseStoreFilePath).exists()
val jarvisApplicationId = providers.gradleProperty("jarvisApplicationId")
    .orElse("com.jarvis.android")
val jarvisVersionCode = providers.gradleProperty("jarvisVersionCode")
    .orNull
    ?.toIntOrNull()

android {
    namespace = "com.jarvis.android"
    compileSdk = 36

    defaultConfig {
        // Keep direct/GitHub APK updates on the legacy ID. Google Play builds
        // override this with -PjarvisApplicationId=com.daraujop77.jarvis.
        applicationId = jarvisApplicationId.get()
        minSdk = 26
        targetSdk = 36
        // Google Play can override versionCode independently so direct APK
        // updates keep their existing version sequence.
        versionCode = jarvisVersionCode ?: 40
        versionName = "0.1.39-copilot-ux-v40"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Checked-in schemas are what Lane C migration tests diff against.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
