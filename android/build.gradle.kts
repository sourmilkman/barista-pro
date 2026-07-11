plugins {
    id("com.android.application")
    kotlin("android")
}

val nativeAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val nativeConfigurations = nativeAbis.associateWith { abi ->
    configurations.create("gdxNatives${abi.replace("-", "").replace("_", "")}")
}

android {
    namespace = "com.sourmilkman.baristapro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sourmilkman.baristapro"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/gdx-natives"))

    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    nativeConfigurations.forEach { (abi, configuration) ->
        add(configuration.name, "com.badlogicgames.gdx:gdx-platform:1.13.1:natives-$abi")
    }
}


val copyGdxNatives = tasks.register("copyGdxNatives") {
    outputs.dir(layout.buildDirectory.dir("generated/gdx-natives"))
    doLast {
        nativeConfigurations.forEach { (abi, configuration) ->
            copy {
                from(configuration.map { zipTree(it) })
                include("*.so")
                into(layout.buildDirectory.dir("generated/gdx-natives/$abi"))
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn(copyGdxNatives) }
