plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.wink.xgjhome"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.wink.xgjhome"
        ndk { abiFilters += listOf("arm64-v8a") }
        minSdk = 29
        targetSdk = 36
        versionCode = 355
        versionName = "235.0"
    }
    signingConfigs {
        create("dist") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("dist")
        }
        debug { signingConfig = signingConfigs.getByName("dist") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("../lib")
        }
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
}
dependencies {
    implementation(project(":dbdown"))
    implementation(files("libs/ffk-classes.jar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.18.0")

}

