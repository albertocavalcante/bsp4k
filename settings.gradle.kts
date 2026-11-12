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

val jsonrpcRepository = providers.gradleProperty("jsonrpc4k.repository").orNull
require(jsonrpcRepository == null || jsonrpcRepository == "github") {
    "jsonrpc4k.repository must be github when set"
}

dependencyResolutionManagement {
    repositories {
        if (jsonrpcRepository == "github") {
            exclusiveContent {
                forRepository {
                    maven {
                        name = "Jsonrpc4kGitHubPackages"
                        url = uri("https://maven.pkg.github.com/albertocavalcante/jsonrpc4k")
                        val registryUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                        val registryToken = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
                        if (!registryToken.isNullOrEmpty()) {
                            require(!registryUser.isNullOrEmpty()) { "A GitHub Packages username is required with a token" }
                            credentials {
                                username = registryUser
                                password = registryToken
                            }
                        }
                    }
                }
                filter {
                    includeModuleByRegex("io\\.github\\.albertocavalcante", "jsonrpc4k-(core|transport)(-.+)?")
                }
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

val jsonrpc4kDir = file("../jsonrpc4k")
val useLocalJsonrpc4k =
    providers
        .gradleProperty("bsp4k.useLocalJsonrpc4k")
        .map { value ->
            require(value == "true" || value == "false") { "bsp4k.useLocalJsonrpc4k must be true or false" }
            value.toBoolean()
        }.getOrElse(true)
if (useLocalJsonrpc4k && jsonrpc4kDir.exists()) {
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
