plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") apply false
}

val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.avapod.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }

    }

    defaultConfig {
        applicationId = "com.avapod.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 24
        versionName = "1.52"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)


    // Firebase Bill of Materials (BoM)
    // Auth 23.2.1 brings reCAPTCHA 18.6.1 instead of the Play-blocked 18.1.2.
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database") // کلمه ktx حذف شد
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    // --- Image Loading (Glide) ---
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // --- Networking (Retrofit & XML Parser) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // --- Media Player (ExoPlayer) ---
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")



    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")


    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.samanzamani:PersianDate:1.7.1")
    implementation("com.google.android.gms:play-services-ads:25.2.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

}


tasks.register("verifyReleaseFirebase") {
    doLast { check(firebaseConfigured) { "Add the real app/google-services.json before building a release." } }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn("verifyReleaseFirebase") }
