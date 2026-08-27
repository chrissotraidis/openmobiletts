import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("OMTTS_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("OMTTS_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("OMTTS_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("OMTTS_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val suppliedReleaseSigningValues = releaseSigningValues.count { it.isPresent }
check(suppliedReleaseSigningValues == 0 || suppliedReleaseSigningValues == releaseSigningValues.size) {
    "Release signing requires all OMTTS_KEYSTORE_* and OMTTS_KEY_* variables"
}
val hasReleaseSigning = suppliedReleaseSigningValues == releaseSigningValues.size

android {
    namespace = "com.openmobiletts.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openmobiletts.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3010001
        versionName = rootProject.file("../VERSION").readText().trim()
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseKeystorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // SvelteKit outputs to _app/ — AAPT ignores underscore-prefixed dirs by default
    androidResources {
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

    // Durable foreground model downloads with network constraints and retry.
    implementation("androidx.work:work-runtime:2.10.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WebView
    implementation("androidx.webkit:webkit:1.10.0")

    // Media session (notification transport controls)
    implementation("androidx.media:media:1.7.0")

    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // One version-locked artifact supplies both Kotlin bindings and JNI libraries.
    implementation("k2-fsa:sherpa-onnx:1.13.4@aar")

    // Archive extraction for model download (tar.bz2)
    implementation("org.apache.commons:commons-compress:1.26.1")

    // PDF text extraction for document upload
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

val installWebDependencies by tasks.registering(Exec::class) {
    workingDir = file("${rootDir}/../client")
    commandLine("npm", "ci")
    inputs.file("${rootDir}/../client/package.json")
    inputs.file("${rootDir}/../client/package-lock.json")
    outputs.dir("${rootDir}/../client/node_modules")
}

val buildWebApp by tasks.registering(Exec::class) {
    dependsOn(installWebDependencies)
    workingDir = file("${rootDir}/../client")
    commandLine("npm", "run", "build")
    inputs.dir("${rootDir}/../client/src")
    inputs.dir("${rootDir}/../client/static")
    inputs.files(
        "${rootDir}/../client/package.json",
        "${rootDir}/../client/package-lock.json",
        "${rootDir}/../client/svelte.config.js",
        "${rootDir}/../client/vite.config.js",
    )
    outputs.dir("${rootDir}/../client/build")
}

val generatedWebAssets = layout.buildDirectory.dir("generated/webappAssets")
val bundleWebApp by tasks.registering(Sync::class) {
    dependsOn(buildWebApp)
    from("${rootDir}/../client/build")
    into(generatedWebAssets.map { it.dir("webapp") })
}

android.sourceSets["main"].assets.srcDir(generatedWebAssets)
val sharedModelCatalog = file("${rootDir}/../models/model-catalog.v1.json")
android.sourceSets["main"].assets.srcDir(sharedModelCatalog.parentFile)

val verifySharedModelCatalog by tasks.registering {
    inputs.file(sharedModelCatalog)
    doLast {
        check(sharedModelCatalog.isFile) { "Missing shared model catalog: $sharedModelCatalog" }
        val catalogText = sharedModelCatalog.readText()
        check(Regex("\\\"schema_version\\\"\\s*:\\s*1").containsMatchIn(catalogText)) {
            "Unsupported shared model catalog schema"
        }
        check(catalogText.contains("kokoro-multi-lang-v1_0")) { "Missing managed TTS model" }
        check(catalogText.contains("kitten-mini-en-v0_8")) { "Missing experimental Kitten Mini model" }
        check(catalogText.contains("kitten-micro-en-v0_8")) { "Missing experimental Kitten Micro model" }
        check(catalogText.contains("sherpa-onnx-moonshine-base-en-int8")) { "Missing managed STT model" }
    }
}

tasks.named("preBuild").configure {
    dependsOn(bundleWebApp, verifySharedModelCatalog)
}
