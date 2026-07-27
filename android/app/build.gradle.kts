import java.io.FileInputStream
import java.util.Properties

// Upload-key signing. The keystore + passwords live OUTSIDE git in
// android/keystore.properties (see keystore.properties.example). When that file
// is absent (e.g. a fresh clone or CI without secrets), release falls back to the
// debug key so the project still builds — but the Play Store upload MUST use the
// real upload key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.plantora.billing"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.plantora.billing"
        minSdk = 24
        targetSdk = 36
        versionCode = 25
        versionName = "0.1.24"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default backend: the hosted production API. Overridable at runtime in
        // Settings (persisted via DataStore) — e.g. http://10.0.2.2:8000 for a
        // local dev backend on the emulator.
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://api.plantbill.in/\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            // Use the NON-optimizing R8 config (still shrinks + obfuscates). The
            // Review-screen freeze reproduces only in the release build, never in
            // debug — the classic signature of an R8 optimization artifact (the
            // -optimize config's inlining/class-merging pass runs only here). This
            // drops that pass as a low-risk, reversible mitigation while the ANR
            // watchdog captures the real frozen stack. Revert to the -optimize
            // file once the ANR trace confirms the true cause.
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )
            // Upload native debug symbols so Play can symbolicate native crashes/ANRs.
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Real upload key when secrets are present; debug key otherwise so the
            // project still builds without them (that build is NOT publishable).
            signingConfig = if (keystorePropsFile.exists())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.acra.http)
    implementation(libs.acra.toast)
    implementation("com.google.zxing:core:3.5.3")
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Hosts the Review-sheet freeze regression test (ReviewSheetFreezeTest). It
    // drives an empty ComponentActivity from ui-test-manifest via ActivityScenario
    // + Compose setContent — deliberately NOT the Compose test rule / Espresso,
    // which crash on new-API emulators (InputManager.getInstance removed).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.test.manifest)
}
