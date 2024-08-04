plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.appdistribution)
}

object Release {
    val versionCode = 9
    val versionName = "1.0.${versionCode - 1}"
}

android {
    namespace = "com.fingerprint.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fingerprint.app"
        minSdk = 26
        targetSdk = 34
        versionCode = Release.versionCode
        versionName = Release.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val distributionDir = "$rootDir/distribution"
            firebaseAppDistribution {
                groups = "all"
                artifactType = "APK"
                releaseNotesFile = "$distributionDir/release-notes.txt"
                serviceCredentialsFile = "$distributionDir/firebase-distribution-key.json"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.fingerprint)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

task("uploadReleaseToAppDistribution") {
    group = "distribution"
    description = "Assembles the release build and uploads it to app distribution"
    dependsOn("clean")
    dependsOn("assembleRelease")
    dependsOn("appDistributionUploadRelease")
}

task<Copy>("moveReleaseApk") {
    val buildDir = layout.projectDirectory.dir("release")
    val destinationDir = layout.projectDirectory.dir("../sample")

    from(buildDir) {
        include("*.apk")
        rename("(.*).apk", "sample.apk")
    }
    into(destinationDir)
    includeEmptyDirs = false

    dependsOn("assembleRelease")
    shouldRunAfter("assembleRelease")
}
