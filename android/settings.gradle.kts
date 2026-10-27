pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
}

include(":app")
include(":app:node_feature")
project(":app:node_feature").projectDir = file("app/src/node_feature")
include(":app:python_feature")
project(":app:python_feature").projectDir = file("app/src/python_feature")
include(":app:clang_feature")
project(":app:clang_feature").projectDir = file("app/src/clang_feature")
include(":app:ty_feature")
project(":app:ty_feature").projectDir = file("app/src/ty_feature")
include(":app:bash_language_server_feature")
project(":app:bash_language_server_feature").projectDir = file("app/src/bash_language_server_feature")
include(":app:copilot_language_server_feature")
project(":app:copilot_language_server_feature").projectDir = file("app/src/copilot_language_server_feature")
include(":app:vscode_langservers_extracted_feature")
project(":app:vscode_langservers_extracted_feature").projectDir = file("app/src/vscode_langservers_extracted_feature")
