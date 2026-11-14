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

val explicitJsonrpc4kDir = providers.gradleProperty("bsp4k.jsonrpc4kBuild").orNull?.let(::file)
require(explicitJsonrpc4kDir == null || explicitJsonrpc4kDir.isDirectory) {
    "bsp4k.jsonrpc4kBuild does not name a directory: ${explicitJsonrpc4kDir?.absolutePath}"
}
val jsonrpc4kDir = explicitJsonrpc4kDir ?: file("../jsonrpc4k")
val useLocalJsonrpc4k =
    providers
        .gradleProperty("bsp4k.useLocalJsonrpc4k")
        .map { value ->
            require(value == "true" || value == "false") { "bsp4k.useLocalJsonrpc4k must be true or false" }
            value.toBoolean()
        }.getOrElse(true)
val runningInCi = providers.environmentVariable("CI").map { it == "true" }.getOrElse(false)

/**
 * Compares the jsonrpc4k checkout's git HEAD with the revision pinned as `bsp4k.jsonrpc4kPin` in
 * gradle.properties. A mismatch (or an unreadable HEAD) fails the build when running in CI and warns locally.
 * `-Pbsp4k.allowJsonrpc4kPinMismatch=true` opts out, for local work against another jsonrpc4k revision.
 */
fun verifyJsonrpc4kPin(jsonrpcDirectory: File) {
    val pin = providers.gradleProperty("bsp4k.jsonrpc4kPin").orNull
    require(pin != null && Regex("[0-9a-f]{40}").matches(pin)) {
        "bsp4k.jsonrpc4kPin in gradle.properties must be a full 40-character lowercase commit SHA, got '$pin'"
    }
    val allowMismatch =
        providers
            .gradleProperty("bsp4k.allowJsonrpc4kPinMismatch")
            .map { value ->
                require(value == "true" || value == "false") { "bsp4k.allowJsonrpc4kPinMismatch must be true or false" }
                value.toBoolean()
            }.getOrElse(false)
    if (allowMismatch) return

    val revision =
        providers.exec {
            commandLine("git", "-C", jsonrpcDirectory.absolutePath, "rev-parse", "HEAD")
            isIgnoreExitValue = true
        }
    val head =
        runCatching {
            if (revision.result.get().exitValue == 0) {
                revision.standardOutput.asText
                    .get()
                    .trim()
            } else {
                null
            }
        }.getOrNull()
    if (head == pin) return

    val found = head ?: "unknown (not a readable git checkout)"
    val message =
        "The jsonrpc4k checkout at ${jsonrpcDirectory.absolutePath} is at $found, but bsp4k pins $pin " +
            "(bsp4k.jsonrpc4kPin in gradle.properties). Check out the pinned revision, or pass " +
            "-Pbsp4k.allowJsonrpc4kPinMismatch=true to build against another revision."
    if (runningInCi) throw GradleException(message)
    Logging.getLogger("bsp4k").warn("WARNING: $message")
}

if (useLocalJsonrpc4k && !jsonrpc4kDir.isDirectory && runningInCi) {
    // A mis-laid-out CI job would otherwise fall back to published coordinates with no pin check.
    throw GradleException(
        "CI builds jsonrpc4k from the sibling checkout, but ${jsonrpc4kDir.absolutePath} does not exist. " +
            "Check out jsonrpc4k at the revision pinned by bsp4k.jsonrpc4kPin in gradle.properties, " +
            "or pass -Pbsp4k.useLocalJsonrpc4k=false to build from published coordinates.",
    )
}

if (useLocalJsonrpc4k && jsonrpc4kDir.isDirectory) {
    verifyJsonrpc4kPin(jsonrpc4kDir)
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
