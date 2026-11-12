import org.gradle.api.artifacts.repositories.MavenArtifactRepository

settingsEvaluated {
    if (rootProject.name != "bsp4k") return@settingsEvaluated
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
    if (name != "bsp4k") return@rootProject
    val group = "io.github.albertocavalcante"
    val modules =
        listOf(
            "jsonrpc4k-core",
            "jsonrpc4k-core-jvm",
            "jsonrpc4k-core-js",
            "jsonrpc4k-core-linuxx64",
            "jsonrpc4k-transport",
            "jsonrpc4k-transport-jvm",
            "jsonrpc4k-transport-macosarm64",
            "bsp4k-route-canary",
            "jsonrpc4k-coreevil",
        )

    fun files(
        module: String,
        version: String,
    ) = configurations
        .detachedConfiguration(dependencies.create("$group:$module:$version"))
        .incoming
        .artifactView {}
        .files
    val artifacts = modules.associateWith { files(it, "1.0") }
    val missing = files("jsonrpc4k-core", "2.0")
    tasks.register("verifyRegistryRoute") {
        doLast {
            for ((module, artifact) in artifacts) {
                val expected = if (module == "bsp4k-route-canary" || module == "jsonrpc4k-coreevil") "fallback" else "primary"
                check(artifact.singleFile.readText() == "$expected:$module") { "Wrong repository for $module" }
            }
            val failure =
                runCatching { missing.singleFile }.exceptionOrNull()
                    ?: error("Missing JSON-RPC artifact incorrectly resolved from fallback")
            check("Could not find $group:jsonrpc4k-core:2.0." in failure.stackTraceToString()) {
                "Resolution failed for an unrelated reason: $failure"
            }
        }
    }
}
