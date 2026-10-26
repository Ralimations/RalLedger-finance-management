plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseStoreFilePath = providers.gradleProperty("ralledgerReleaseStoreFile")
    .orElse(providers.environmentVariable("RALLEDGER_RELEASE_STORE_FILE")).orNull
val releaseStorePasswordValue = providers.gradleProperty("ralledgerReleaseStorePassword")
    .orElse(providers.environmentVariable("RALLEDGER_RELEASE_STORE_PASSWORD")).orNull
val releaseKeyAliasValue = providers.gradleProperty("ralledgerReleaseKeyAlias")
    .orElse(providers.environmentVariable("RALLEDGER_RELEASE_KEY_ALIAS")).orNull
val releaseKeyPasswordValue = providers.gradleProperty("ralledgerReleaseKeyPassword")
    .orElse(providers.environmentVariable("RALLEDGER_RELEASE_KEY_PASSWORD")).orNull
val releaseSigningValues = listOf(
    releaseStoreFilePath,
    releaseStorePasswordValue,
    releaseKeyAliasValue,
    releaseKeyPasswordValue
)
val hasPartialReleaseSigning = releaseSigningValues.any { it != null } && releaseSigningValues.any { it == null }
if (hasPartialReleaseSigning) {
    throw org.gradle.api.GradleException(
        "Release signing needs all four RalLedger keystore settings. See RELEASE_SIGNING.md."
    )
}
val releaseStoreFile = releaseStoreFilePath?.let(::file)
if (releaseStoreFile != null && !releaseStoreFile.isFile) {
    throw org.gradle.api.GradleException("RalLedger release keystore does not exist: $releaseStoreFile")
}

android {
    namespace = "com.ralledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ralledger"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] =
            "com.ralledger.ui.onboarding.UpgradeDataCompatibilityInstrumentedTest"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePasswordValue!!
                keyAlias = releaseKeyAliasValue!!
                keyPassword = releaseKeyPasswordValue!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.biometric)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & Coroutines
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
