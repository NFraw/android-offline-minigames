plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val targetAbis = providers.gradleProperty("targetAbis").orElse("arm64-v8a").get().split(",")
require(targetAbis.all { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") })

android {
    namespace = "com.minigames.collection"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minigames.collection.next"
        ndk { abiFilters += targetAbis }
        minSdk = 33
        targetSdk = 36
        versionCode = 40
        versionName = "3.9.0-updates"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = "minigames123"
            keyAlias = "minigames"
            keyPassword = "minigames123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // 离线 APK 压缩原生库，默认只带手机 arm64；模拟器由脚本指定 x86_64。
    packaging { jniLibs.useLegacyPackaging = true }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/godotAssets"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.godotengine:godot:4.7.2.stable")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// 37 款游戏与大厅以一个 PCK 共用同一个 Godot 运行时。
val prepareGodotPack by tasks.registering(Exec::class) {
    inputs.files(fileTree(rootProject.file("godot")) {
        exclude(".godot/**", "android/**", "*.tmp")
    })
    inputs.files(rootProject.file("scripts/Build-GodotPack.ps1"), rootProject.file("scripts/Common.ps1"), rootProject.file("scripts/Sync-GodotCatalog.ps1"), file("src/main/java/com/minigames/collection/common/GameSpec.kt"), rootProject.file("dev.local.json"))
    outputs.file(layout.buildDirectory.file("generated/godotAssets/games.pck"))
    workingDir(rootProject.projectDir)
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", rootProject.file("scripts/Build-GodotPack.ps1").absolutePath)
}
tasks.named("preBuild") { dependsOn(prepareGodotPack) }




