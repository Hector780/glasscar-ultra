plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.glasscar.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glasscar.launcher"
        resValue("string", "app_name", "GlassCar Ultra")
        minSdk = 28
        targetSdk = 34
        versionCode = 8
        versionName = "4.4-ts10-media"
        buildConfigField("boolean", "REAL_APP_EMBEDDING", "false")
        val mapsKey = providers.gradleProperty("MAPS_API_KEY").orElse(providers.environmentVariable("MAPS_API_KEY")).orElse("").get()
        resValue("string", "google_maps_key", mapsKey)
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", mapsKey.isNotBlank().toString())
    }

    buildTypes {
        create("embedding") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".embedtest"
            versionNameSuffix = "-real-app-prototype"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "REAL_APP_EMBEDDING", "true")
            resValue("string", "app_name", "GlassCar Apps Reales Prueba")
        }
        create("cameradiag") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".cameradiag"
            versionNameSuffix = "-camera-diagnostic"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "GlassCar Cámaras Diagnóstico")
        }
        create("review") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".ts10test"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "GlassCar 4.2 Prueba")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        aidl = true
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Maps transitively brings Fragment 1.0; Activity Result requires a current implementation.
    implementation("androidx.fragment:fragment:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
