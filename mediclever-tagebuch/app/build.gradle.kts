plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Robolectric laedt seine Android-Laufzeit sonst selbst aus dem Netz. Hier holt Gradle sie einmal.
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(robolectricDir)
}

android {
    namespace = "de.mediclever.tagebuch"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "de.mediclever.tagebuch"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.dependsOn(robolectricJars)
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-14663667-2-i7")
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
