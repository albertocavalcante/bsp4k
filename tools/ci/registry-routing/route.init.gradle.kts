import org.gradle.api.artifacts.repositories.MavenArtifactRepository

settingsEvaluated {
    val repositories = dependencyResolutionManagement.repositories
    val selected = providers.gradleProperty("jsonrpc4k.repository").orNull == "github"
    val route = repositories.findByName("Jsonrpc4kGitHubPackages") as? MavenArtifactRepository
    if (selected) {
        checkNotNull(route) { "Selected JSON-RPC repository missing" }
        check(route.url.toString() == "https://maven.pkg.github.com/albertocavalcante/jsonrpc4k")
        route.url = uri(requireNotNull(System.getenv("BSP_ROUTE_PRIMARY")))
        repositories.toList().filter { it !== route }.forEach { repositories.remove(it) }
        repositories.maven {
            name = "RouteFixtureFallback"
            url = uri(requireNotNull(System.getenv("BSP_ROUTE_FALLBACK")))
        }
    } else {
        check(route == null) { "JSON-RPC repository enabled without selection" }
    }
}

gradle.rootProject {
    tasks.register("verifyRegistryRoute") {
        doLast {
            val group = "io.github.albertocavalcante"

            fun resolve(
                module: String,
                version: String,
            ) = configurations.detachedConfiguration(dependencies.create("$group:$module:$version")).singleFile.readText()
            for (module in listOf(
                "jsonrpc4k-core",
                "jsonrpc4k-core-jvm",
                "jsonrpc4k-core-js",
                "jsonrpc4k-core-linuxx64",
                "jsonrpc4k-transport",
                "jsonrpc4k-transport-jvm",
                "jsonrpc4k-transport-macosarm64",
            )) {
                check(resolve(module, "1.0") == "primary:$module") { "Wrong repository for $module" }
            }
            check(resolve("bsp4k-route-canary", "1.0") == "fallback:bsp4k-route-canary")
            check(resolve("jsonrpc4k-coreevil", "1.0") == "fallback:jsonrpc4k-coreevil")
            val failure =
                runCatching { resolve("jsonrpc4k-core", "2.0") }.exceptionOrNull()
                    ?: error("Missing JSON-RPC artifact incorrectly resolved from fallback")
            check("Could not find $group:jsonrpc4k-core:2.0." in failure.stackTraceToString()) {
                "Resolution failed for an unrelated reason: $failure"
            }
        }
    }
}
