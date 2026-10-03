plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.daxiaamu.dbdown"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "UPDATE_REPOSITORY", "\"daxiaamu/DBdown\"")
        buildConfigField("String", "UPDATE_BRANCH", "\"main\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    compileOnly(files("/home/z/my-project/android_build/ffk_out/classes.jar"))
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-muxer:1.9.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.0")

}
