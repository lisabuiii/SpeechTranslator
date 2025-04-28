plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.speechtranslator"
    compileSdk = 35

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/INDEX.LIST")
    }
    defaultConfig {
        applicationId = "com.example.speechtranslator"
        minSdk = 24
        targetSdk = 35
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

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.cloud:google-cloud-speech:4.54.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.33.1")
    implementation("com.google.auth:google-auth-library-credentials:1.33.1")
    implementation("com.google.cloud:google-cloud-texttospeech:2.60.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("io.grpc:grpc-okhttp:1.71.0")
    implementation("io.grpc:grpc-stub:1.71.0")
    implementation("com.google.api:gax:1.71.0")
    implementation("io.grpc:grpc-api:1.71.0")
    implementation("io.grpc:grpc-core:1.71.0")
    implementation(libs.espresso.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}