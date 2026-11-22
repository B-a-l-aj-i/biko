import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// The release key lives outside the repo, in the user's ~/.android folder.
// Without that file the release build is simply left unsigned.
val releaseKeyFile = File(System.getProperty("user.home"), ".android/biko-keystore.properties")
val releaseKey = Properties().apply { if (releaseKeyFile.exists()) releaseKeyFile.inputStream().use { load(it) } }

android {
    namespace = "com.budgetapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.budgetapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "DEMO", "false")
    }

    signingConfigs {
        if (releaseKeyFile.exists()) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeyFile.exists()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // The debug build with debugging switched off. Debuggable apps run
        // Compose several times slower, so animations stutter in a way a real
        // release never would; install this one to judge smoothness. Signed
        // with the debug key so it installs over a debug build and keeps its data.
        create("smooth") {
            initWith(getByName("debug"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "debug"
        }
        // For screenshots: installs beside the real app (its own id and data) and
        // starts filled with made-up payments. Never shipped.
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            buildConfigField("boolean", "DEMO", "true")
            matchingFallbacks += "debug"
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.haze)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    // Android's built-in org.json is only a stub in unit tests; this is the real thing.
    testImplementation("org.json:json:20240303")
}
