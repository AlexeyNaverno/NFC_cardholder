import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Release signing is optional and never committed.
 *
 * Either create `keystore.properties` in the repository root (it is git-ignored):
 *
 *     storeFile=/absolute/path/to/release.jks
 *     storePassword=...
 *     keyAlias=...
 *     keyPassword=...
 *
 * or export the environment variables NFC_STORE_FILE, NFC_STORE_PASSWORD,
 * NFC_KEY_ALIAS and NFC_KEY_PASSWORD (this is what CI does).
 *
 * When neither is present, `assembleRelease` produces an unsigned APK, which is
 * exactly what F-Droid expects: it signs the app with its own key.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.alexnaverno.nfccardholder"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.alexnaverno.nfccardholder"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "NFC_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "NFC_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NFC_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NFC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            isMinifyEnabled = false
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)

    testImplementation(libs.junit)
}
