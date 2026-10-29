plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cn.baymaxawa.xmsfnotichannel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cn.baymaxawa.xmsfnotichannel"
        minSdk = 33
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 固定签名：keystore 随仓库提交，保证任何机器 / 任何一次 CI 产出的 APK
        // 签名完全一致，覆盖安装不会再报「签名不一致」。
        // 若想改用 Secrets 注入，删掉这一份、恢复用环境变量读路径即可。
        create("shared") {
            storeFile = file("../xmsfnotichannel.keystore")
            storePassword = "xmsfnotichannel"
            keyAlias = "xmsfnotichannel"
            keyPassword = "xmsfnotichannel"
        }
    }
    buildTypes {
        debug {
            // debug 也用同一份签名，否则 debug / release 互相覆盖不了
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 首页状态卡要显示 versionName，AGP 8+ 默认不再生成 BuildConfig
        buildConfig = true
    }
    packaging {
        resources {
            merges += listOf("META-INF/xposed/*")
        }
        dex {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":hook"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.navigation)
    implementation(libs.material.icons)
    implementation(libs.haze)
    implementation(libs.hiddenapibypass)
}
