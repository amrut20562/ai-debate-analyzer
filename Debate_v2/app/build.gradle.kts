plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.debate_v2"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.debate_v2"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {



    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Firebase
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    implementation("com.github.Dimezis:BlurView:version-2.0.2")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Circle ImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Agora Video SDK
    implementation("io.agora.rtc:full-sdk:4.3.2")

    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")


    // Permissions
    implementation("com.github.permissions-dispatcher:permissionsdispatcher:4.9.2")
    annotationProcessor("com.github.permissions-dispatcher:permissionsdispatcher-processor:4.9.2")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:23.4.0")

    implementation("com.android.volley:volley:1.2.1")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")

    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

    // Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
