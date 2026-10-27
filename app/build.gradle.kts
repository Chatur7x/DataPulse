plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.datapulse.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.datapulse.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true // java.time on API 23-25 (§C)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

}

ksp {
    // §7: schema JSON committed to git; migrations tested, never destructive post-1.0
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Compose BOM — ponytail: one BOM over 20 loose versions
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    // Hilt (+ WorkManager integration; v1.0 omitted hilt-work)
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // DataStore: Preferences flavour (settings only — plans live in Room, §4 rule #1)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Background / charts / widget
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // Test
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.3")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.57.2")
}
