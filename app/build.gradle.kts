import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.appdistribution)
}

object Release {
    val versionCode = 13
    val versionName = "1.0.${versionCode - 1}"
}

val getKey = { key: String -> getLocalProperty(key, root = rootDir.path) }

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
    }

    signingConfigs {
        create("release") {
            keyAlias = getKey("keyAlias")
            keyPassword = getKey("keyPassword")
            storeFile = file(getKey("storeFile"))
            storePassword = getKey("storePassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
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

fun getLocalProperty(key: String, file: String? = null, root: String = "."): String {
    val properties = Properties()
    val defaultFiles = listOf("$root/local.properties", "$root/defaults.properties")
    val files = (defaultFiles + file).mapNotNull { it }

    files.forEach {
        val localProperties = File(it)
        if (localProperties.isFile) {
            runCatching {
                InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
                    if (properties[key] in listOf(null, ""))
                        properties.load(reader)
                }
            }
        }
    }

    return properties.getProperty(key).toString()
}
