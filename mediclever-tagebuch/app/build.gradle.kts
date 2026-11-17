import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Ohne google-services.json baut die App trotzdem und laeuft dann nur lokal (kein Konto, kein Abgleich).
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) apply(plugin = libs.plugins.google.services.get().pluginId)

// Robolectric laedt seine Android-Laufzeit sonst selbst aus dem Netz. Hier holt Gradle sie einmal.
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(robolectricDir)
}

// Signatur fuer den Play Store: keystore.properties liegt nie im Repository.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { stream -> load(stream) } }
}

android {
    namespace = "de.mediclever.tagebuch"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "de.mediclever.tagebuch"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.3.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())
        // -PfirebaseEmulator=true verbindet Debug-Builds mit dem lokalen Firebase-Emulator (siehe firebase/README.md)
        buildConfigField("boolean", "FIREBASE_EMULATOR", (providers.gradleProperty("firebaseEmulator").orNull == "true").toString())
        // Web-Client-ID aus der Firebase-Konsole fuer die Google-Anmeldung, siehe docs/firebase-einrichtung.md
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").orNull ?: ""}\"")
    }
    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
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
                it.maxHeapSize = "3g"
            }
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.glance.appwidget)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)

    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-14663667-2-i7")
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
