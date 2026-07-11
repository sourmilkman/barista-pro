plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.sourmilkman.baristapro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sourmilkman.baristapro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86_64")
}
