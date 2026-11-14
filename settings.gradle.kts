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
val jsonrpc4kPin = providers.gradleProperty("jsonrpc4k.rev").orNull
require(jsonrpc4kPin != null && Regex("[0-9a-f]{40}").matches(jsonrpc4kPin)) {
    "jsonrpc4k.rev in gradle.properties must be a full 40-character lowercase commit SHA"
}
val allowJsonrpc4kMismatch =
    providers
        .gradleProperty("bsp4k.allowJsonrpc4kMismatch")
        .map { value ->
            require(value == "true" || value == "false") { "bsp4k.allowJsonrpc4kMismatch must be true or false" }
            value.toBoolean()
        }.getOrElse(false)
val runningInCi = providers.environmentVariable("CI").map { it == "true" }.getOrElse(false)

if (useLocalJsonrpc4k && !jsonrpc4kDir.exists() && runningInCi) {
    // A mis-laid-out CI job would otherwise fall back to published coordinates with no pin check.
    throw GradleException(
        "CI builds jsonrpc4k from the sibling checkout, but ${jsonrpc4kDir.absolutePath} does not exist. " +
            "Check out jsonrpc4k at the revision pinned by jsonrpc4k.rev in gradle.properties, " +
            "or pass -Pbsp4k.useLocalJsonrpc4k=false to build from published coordinates.",
    )
}

if (useLocalJsonrpc4k && jsonrpc4kDir.exists()) {
    // The sibling checkout must be the pinned revision: fail in CI, warn locally. Local work against
    // another revision opts out with -Pbsp4k.allowJsonrpc4kMismatch=true.
    val siblingHead =
        try {
            val head =
                providers.exec {
                    commandLine("git", "-C", jsonrpc4kDir.absolutePath, "rev-parse", "HEAD")
                    isIgnoreExitValue = true
                }
            if (head.result.get().exitValue == 0) {
                head.standardOutput.asText
                    .get()
                    .trim()
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    if (siblingHead != jsonrpc4kPin) {
        val found = siblingHead ?: "an unknown revision (not a git checkout?)"
        val message =
            "The jsonrpc4k checkout at ${jsonrpc4kDir.absolutePath} is at $found, " +
                "but gradle.properties pins jsonrpc4k.rev=$jsonrpc4kPin. " +
                "Check out the pinned revision or pass -Pbsp4k.allowJsonrpc4kMismatch=true to build against another one."
        val log = Logging.getLogger("bsp4k-settings")
        when {
            allowJsonrpc4kMismatch -> log.warn("warning: jsonrpc4k pin mismatch allowed. $message")
            runningInCi -> throw GradleException(message)
            else -> log.warn("warning: $message")
        }
    }
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
