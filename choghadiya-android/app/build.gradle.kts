import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is read from choghadiya-android/keystore.properties, which is git-ignored
// and never committed. Without it the release APK is built unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.kbsc.choghadiya"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kbsc.choghadiya"
        minSdk = 26          // Android 8.0+; java.time is built in
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true        // R8: shrink and obfuscate
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        viewBinding = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Optional: use a pre-downloaded Robolectric runtime (set ROBOLECTRIC_DEPS_DIR) to avoid network fetches.
            System.getenv("ROBOLECTRIC_DEPS_DIR")?.let {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", it)
            }
        }
    }
    lint {
        // Security-relevant checks fail the build instead of only warning.
        error += listOf(
            "ExportedReceiver", "ExportedService", "ExportedContentProvider", "SetJavaScriptEnabled",
            "AllowBackup", "HardcodedDebugMode", "WorldReadableFiles", "WorldWriteableFiles",
            "UnsafeProtectedBroadcastReceiver", "UnprotectedSMSBroadcastReceiver", "TrustAllX509TrustManager",
            "BadHostnameVerifier", "SSLCertificateSocketFactoryGetInsecure", "PackagedPrivateKey",
        )
        checkReleaseBuilds = true
    }
}

configurations.configureEach {
    // Not needed by this app; dropping it removes an exported (DUMP-protected) receiver,
    // so the launcher screen is the only component reachable from other apps.
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
}
