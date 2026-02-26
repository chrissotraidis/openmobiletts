plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openmobiletts.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.openmobiletts.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // SvelteKit outputs to _app/ — AAPT ignores underscore-prefixed dirs by default
    aaptOptions {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WebView
    implementation("androidx.webkit:webkit:1.10.0")

    // Media session (notification transport controls)
    implementation("androidx.media:media:1.7.0")

    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Sherpa-ONNX — JNI .so files in app/src/main/jniLibs/, Kotlin API in source
    // No AAR needed: native libs are bundled directly

    // Archive extraction for model download (tar.bz2)
    implementation("org.apache.commons:commons-compress:1.26.1")

    // PDF text extraction for document upload
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

tasks.register<Exec>("bundleWebApp") {
    workingDir = file("${rootDir}/../client")
    commandLine("bash", "-c", "npm run build && rm -rf ${projectDir}/src/main/assets/webapp && cp -r build ${projectDir}/src/main/assets/webapp")
}
