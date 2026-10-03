@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        moduleName.set("tasks_app")
    }
}

android {
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        lintConfig = file("lint.xml")
        textOutput = File("stdout")
        textReport = true
    }

    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        testApplicationId = "org.tasks.test"
        applicationId = libs.versions.applicationId.get()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // The 4Link library needs Android 13 (API 33), the same floor as 4Dictate.
        minSdk = 33
        testInstrumentationRunner = "org.tasks.TestRunner"
        // AppAuth's library manifest needs this placeholder, but its RedirectUriReceiverActivity is replaced in the generic manifest
        // (the redirect is msauth://<package>/<hash>, see SyncClients), so no `org.tasks` scheme can reach the merged manifest.
        manifestPlaceholders["appAuthRedirectScheme"] = "msauth"
    }

    // Release builds are signed with the family release key, so 4Tasks is "family" to 4Dictate.
    // The key never lives in this repository: point FOURTASKS_SIGNING_PROPERTIES (or the Gradle
    // property fourtasksSigningProperties) at a properties file with storeFile, storePassword,
    // keyAlias and keyPassword. Without it the release build is unsigned.
    val signingProperties = Properties().apply {
        val path = System.getenv("FOURTASKS_SIGNING_PROPERTIES")
            ?: (findProperty("fourtasksSigningProperties") as String?)
        if (path != null) file(path).takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }
    val canSignRelease = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !signingProperties.getProperty(it).isNullOrBlank() }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile").trim())
                storePassword = signingProperties.getProperty("storePassword").trim()
                keyAlias = signingProperties.getProperty("keyAlias").trim()
                keyPassword = signingProperties.getProperty("keyPassword").trim()
                storeType = "PKCS12"
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions += listOf("store")

    @Suppress("LocalVariableName")
    buildTypes {
        debug {
            val tasks_mapbox_key_debug: String? by project
            val tasks_google_key_debug: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key_debug ?: "")
            resValue("string", "google_key", tasks_google_key_debug ?: "")
            resValue("string", "posthog_key", "")
            enableUnitTestCoverage = project.hasProperty("coverage")
            enableAndroidTestCoverage = project.hasProperty("coverage")
        }
        release {
            val tasks_mapbox_key: String? by project
            val tasks_google_key: String? by project
            val tasks_posthog_key: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key ?: "")
            resValue("string", "google_key", tasks_google_key ?: "")
            resValue("string", "posthog_key", tasks_posthog_key ?: "")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    productFlavors {
        create("generic") {
            isDefault = true
            dimension = "store"
        }
    }
    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/INDEX.LIST")
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Shared test helpers replace Android-test symlinks, which are not portable to Windows.
        getByName("test").java.directories.add("src/sharedTest/java")
        getByName("androidTest").java.directories.add("src/sharedTest/java")
    }

    namespace = "org.tasks"
}

configurations.all {
    exclude(group = "org.apache.httpcomponents")
    exclude(group = "org.checkerframework")
    exclude(group = "com.google.code.findbugs")
    exclude(group = "com.google.errorprone")
    exclude(group = "com.google.j2objc")
    exclude(group = "com.google.http-client", module = "google-http-client-apache-v2")
    exclude(group = "com.google.http-client", module = "google-http-client-jackson2")
}

val genericImplementation by configurations

dependencies {
    implementation(projects.data)
    implementation(project(":fourlink"))
    // Double Metaphone, to match a misheard task title by sound (4Link suggestions).
    implementation("commons-codec:commons-codec:1.17.1")
    implementation(projects.kmp)
    implementation(libs.kermit)
    implementation(projects.icons)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.adaptive.navigation.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.bitfire.ical4android) {
        exclude(group = "commons-logging")
        exclude(group = "org.json", module = "json")
        exclude(group = "org.codehaus.groovy", module = "groovy")
        exclude(group = "org.codehaus.groovy", module = "groovy-dateutil")
    }
    implementation(libs.dmfs.opentasks.provider) {
        exclude("com.github.tasks.opentasks", "opentasks-contract")
    }
    implementation(libs.dmfs.rfc5545.datetime)
    implementation(libs.dmfs.recur)
    implementation(libs.dmfs.jems)

    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.core.remoteviews)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.room)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.appcompat)
    implementation(libs.iconics)
    implementation(libs.markwon)
    implementation(libs.markwon.editor)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation(libs.kotlin.reflect)

    implementation(libs.kotlin.jdk8)
    implementation(libs.kotlinx.immutable)
    implementation(libs.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.persistent.cookiejar)
    implementation(libs.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.preference)
    implementation(libs.timber)
    implementation(libs.dashclock.api)
    implementation(libs.locale) {
        isTransitive = false
    }
    implementation(libs.jchronic) {
        isTransitive = false
    }
    implementation(libs.shortcut.badger)
    implementation(libs.google.api.drive)
    implementation(libs.google.oauth2)
    implementation(libs.androidx.work)
    implementation(libs.etebase)
    implementation(libs.colorpicker)
    implementation(libs.appauth)
    implementation(libs.osmdroid)
    implementation(libs.androidx.recyclerview)

    implementation(platform(libs.androidx.compose))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    implementation(libs.ktor)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    implementation(libs.accompanist.permissions)

    androidTestImplementation(libs.dagger.hilt.testing)
    kspAndroidTest(libs.dagger.hilt.compiler)
    kspAndroidTest(libs.androidx.hilt.compiler)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.make.it.easy)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)

    testImplementation(libs.junit)
    // android.jar's org.json is stubbed in unit tests; the real one lets the 4Link tests read and write JSON.
    testImplementation("org.json:json:20231013")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.make.it.easy)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.xpp3)
}
