plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.lavescar.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lavescar.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

baselineProfile {
    dexLayoutOptimization = true
}

dependencies {
    implementation(project(":launcher-core"))
    implementation(project(":system-integration"))
    implementation(project(":data"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)

    baselineProfile(project(":benchmark"))
}
