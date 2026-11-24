plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ice.nihongo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ice.nihongo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a") } // ROG Phone 5 是 arm64
        externalNativeBuild {
            cmake {
                arguments += listOf("-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF", "-DANDROID_STL=c++_static")
                cppFlags += listOf("-O3")
            }
        }
    }

    buildTypes {
        debug {
            // 讓 C++ 也用最佳化編譯，不然 Whisper 會慢很多
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    androidResources { noCompress += "bin" } // 模型不壓縮，讀取更快
    buildFeatures { compose = true }
    packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.md", "/META-INF/*.txt") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0") // 日文分詞與讀音
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
