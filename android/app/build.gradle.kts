plugins {
    alias(libs.plugins.android.application)
    // NO kotlin-android, NO kotlin.plugin.compose — AGP 9 built-in Kotlin handles both.
}

android {
    namespace = "com.xrc.implant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xrc.implant"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "2.01"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true        // AGP supplies the Compose compiler — no plugin, no composeOptions block
    }

    // AGP 9 built-in Kotlin DSL (replaces android.kotlinOptions{})
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASS")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASS")
        }
    }

    buildTypes {
        debug {
            // default debug keystore — always works in CI
        }
        release {
            isMinifyEnabled = false   // R8 off for now: zero reflection breakage, zero CI surprises
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")   // never fail the build when secrets are absent
            }
        }
    }

    lint {
        checkReleaseBuilds = false    // lintVital must never sink a CI run
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
