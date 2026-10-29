import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// ---------------------------------------------------------------------------
// Payload bundling for sideload / offline builds
//
// Runtime and extension payloads live in dynamic-feature modules, which a plain
// `flutter build apk` does not package (only `bundleRelease` sees them). Selecting
// payloads copies their assets/ and jniLibs/ into the base module so a single
// self-contained APK ships them, with no AAB + bundletool round-trip.
//
//   ORG_GRADLE_PROJECT_payloads=all flutter build apk --release
//   ./gradlew :app:assembleRelease -Ppayloads=python,node
//
// Unset => nothing is staged, so the Play / AAB path stays lean.
// ---------------------------------------------------------------------------
val payloadsCatalog = Properties().apply {
    val catalogFile = rootProject.file("payloads.properties")
    if (catalogFile.exists()) catalogFile.inputStream().use { load(it) }
}

val requestedPayloads: List<String> =
    (project.findProperty("payloads") as String? ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .let { selection ->
            if (selection.contains("all")) payloadsCatalog.stringPropertyNames().sorted() else selection
        }

val unknownPayloads = requestedPayloads.filterNot { payloadsCatalog.containsKey(it) }
require(unknownPayloads.isEmpty()) {
    "Unknown payload module(s): ${unknownPayloads.joinToString()}. " +
        "Valid keys: ${payloadsCatalog.stringPropertyNames().sorted().joinToString()}"
}

val stagedPayloadsDir = layout.buildDirectory.dir("staged-payloads")

val stagePayloads = tasks.register<Copy>("stagePayloads") {
    group = "build"
    description = "Stage selected dynamic-feature payloads into the base module."
    requestedPayloads.forEach { key ->
        val featureMain = file("src/${payloadsCatalog.getProperty(key)}/src/main")
        from(featureMain.resolve("assets")) {
            include("*.zip")
            into("assets")
        }
        from(featureMain.resolve("jniLibs/arm64-v8a")) {
            include("*.so")
            into("jniLibs/arm64-v8a")
        }
    }
    into(stagedPayloadsDir)
}

android {
    namespace = "com.roxum"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.roxum"
        minSdk = 26
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"    
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
            )
            excludes += setOf("**/armeabi-v7a/**", "**/x86_64/**")
        }
    }

    dynamicFeatures.addAll(
        setOf(
            ":app:node_feature",
            ":app:python_feature",
            ":app:clang_feature",
            ":app:ty_feature",
            ":app:bash_language_server_feature",
            ":app:copilot_language_server_feature",
            ":app:vscode_langservers_extracted_feature",
        )
    )

    if (requestedPayloads.isNotEmpty()) {
        // AGP 9 rejects Provider<Directory> in sourceSets, so resolve to File.
        // The stagePayloads dependency is wired explicitly below.
        val stagedRoot = stagedPayloadsDir.get().asFile
        sourceSets.getByName("main") {
            assets.srcDir(stagedRoot.resolve("assets"))
            jniLibs.srcDir(stagedRoot.resolve("jniLibs"))
        }
    }
}

if (requestedPayloads.isNotEmpty()) {
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(stagePayloads) }
    tasks.matching {
        it.name.startsWith("merge") &&
            (it.name.endsWith("Assets") || it.name.endsWith("JniLibFolders"))
    }.configureEach { dependsOn(stagePayloads) }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}


dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.play:feature-delivery:2.1.0")
}


flutter {
    source = "../.."
}
