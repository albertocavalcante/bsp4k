pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bsp4k"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

val jsonrpc4kDir = file("../jsonrpc4k")
if (jsonrpc4kDir.exists()) {
    includeBuild(jsonrpc4kDir) {
        dependencySubstitution {
            substitute(module("io.github.albertocavalcante:jsonrpc4k-core")).using(project(":jsonrpc4k-core"))
            substitute(module("io.github.albertocavalcante:jsonrpc4k-transport")).using(project(":jsonrpc4k-transport"))
        }
    }
}

include(":bsp4k-protocol")
include(":bsp4k-server")
include(":bsp4k-client")
include(":example")
