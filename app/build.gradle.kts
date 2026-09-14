import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val posthogProperties = Properties().apply {
    rootProject.file(".env").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val posthogProjectToken = providers.environmentVariable("POSTHOG_PROJECT_TOKEN").orNull
    ?: posthogProperties.getProperty("POSTHOG_PROJECT_TOKEN").orEmpty()
val posthogHost = providers.environmentVariable("POSTHOG_HOST").orNull
    ?: posthogProperties.getProperty("POSTHOG_HOST").orEmpty()

// Release signing. Secrets never live in the repo: they come from a git-ignored
// keystore.properties (see keystore.properties.example) or, on CI, from environment
// variables. With neither present the release build still assembles, unsigned.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingSecret(key: String, env: String): String? =
    (providers.environmentVariable(env).orNull ?: keystoreProperties.getProperty(key))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("storeFile", "KEPT_KEYSTORE_FILE")
val releaseStorePassword = signingSecret("storePassword", "KEPT_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingSecret("keyAlias", "KEPT_KEY_ALIAS")
val releaseKeyPassword = signingSecret("keyPassword", "KEPT_KEY_PASSWORD")
val releaseSigningParts = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningParts.all { it != null } && rootProject.file(releaseStoreFile!!).exists()
if (!hasReleaseSigning && releaseSigningParts.any { it != null }) {
    // Half-configured is the dangerous case: silently shipping an unsigned APK to someone who
    // believes they set signing up. Unconfigured-on-purpose stays quiet.
    logger.warn("KEPT: release signing is only partly configured - the release build will be UNSIGNED. See keystore.properties.example.")
}

android {
    namespace = "com.example.kept"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenai.kept"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.example.kept.HiltTestRunner"
        buildConfigField("String", "POSTHOG_PROJECT_TOKEN", "\"$posthogProjectToken\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DEBUG_SEED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DEBUG_SEED", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.coroutines.android)
    implementation("com.posthog:posthog-android:3.64.0")

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.hilt.compiler)
}
