import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm") version "2.3.0"
    application
}

kotlin { jvmToolchain(17) }

val version = requireNotNull(System.getenv("BSP_FIXTURE_VERSION"))
val artifacts = listOf("bsp4k-protocol", "bsp4k-client", "bsp4k-server")
val form = requireNotNull(System.getenv("BSP_FIXTURE_FORM"))
require(form == "root" || form == "jvm")
val probes =
    artifacts.flatMap { listOf(it, "$it-jvm") }.associateWith { module ->
        configurations.create("java16-$module") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 16)
            }
        }
    }

dependencies {
    listOf("bsp4k-client", "bsp4k-server").forEach { artifact ->
        val module = if (form == "jvm") "$artifact-jvm" else artifact
        implementation("io.github.albertocavalcante:$module:$version")
    }
    probes.forEach { (module, configuration) ->
        add(configuration.name, "io.github.albertocavalcante:$module:$version")
    }
}

application { mainClass = "MainKt" }

tasks.register("verifyJava16Rejected") {
    doLast {
        probes.forEach { (module, configuration) ->
            val failure =
                runCatching { configuration.resolve() }.exceptionOrNull()
                    ?: error("A Java 16 consumer unexpectedly resolved $module")
            val detail = failure.stackTraceToString()
            val coordinate = "io.github.albertocavalcante:$module:$version"
            check(
                ("No matching variant of $coordinate" in detail && "Java 16" in detail && "Java 17" in detail) ||
                    ("JVM runtime version 16" in detail && "'$coordinate' is only compatible with JVM runtime version 17" in detail),
            ) {
                "Java 16 probe failed for an unrelated reason: $detail"
            }
        }
    }
}
