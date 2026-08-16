plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// libGDX natives：.so 位于 natives jar 根目录，AGP 不识别，需解压到 jniLibs 目录
val nativesConfig = configurations.create("natives")

android {
    namespace = "com.minigames.collection"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("jniLibs"))
        }
    }

    defaultConfig {
        applicationId = "com.minigames.collection"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    // libGDX：2D 游戏框架（GPU 渲染/粒子/动画/Scene2D UI）
    implementation("com.badlogicgames.gdx:gdx:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-freetype:1.13.1")
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        add("natives", "com.badlogicgames.gdx:gdx-platform:1.13.1:natives-$abi")
        add("natives", "com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-$abi")
    }
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// 将 natives 配置中的 .so 解压到 build/jniLibs/<abi>/，供 AGP 打包进 APK
val copyAndroidNatives by tasks.registering {
    doFirst {
        val destRoot = layout.buildDirectory.dir("jniLibs").get().asFile
        nativesConfig.files.forEach { jar ->
            val abi = when {
                jar.name.contains("x86_64") -> "x86_64"
                jar.name.contains("armeabi-v7a") -> "armeabi-v7a"
                jar.name.contains("arm64-v8a") -> "arm64-v8a"
                jar.name.contains("x86") -> "x86"
                else -> return@forEach
            }
            val out = destRoot.resolve(abi).apply { mkdirs() }
            copy {
                from(zipTree(jar))
                into(out)
                include("*.so")
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("NativeLibs")) {
        dependsOn(copyAndroidNatives)
    }
}