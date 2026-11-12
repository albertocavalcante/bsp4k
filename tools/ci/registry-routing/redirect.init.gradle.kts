import org.gradle.api.artifacts.repositories.MavenArtifactRepository

settingsEvaluated {
    if (rootProject.name != "bsp4k") return@settingsEvaluated
    val repository = dependencyResolutionManagement.repositories.findByName("Jsonrpc4kGitHubPackages") as? MavenArtifactRepository
    checkNotNull(repository) { "Selected JSON-RPC repository missing" }
    check(repository.url.toString() == "https://maven.pkg.github.com/albertocavalcante/jsonrpc4k")
    repository.url = uri(requireNotNull(System.getenv("BSP_FIXTURE_REPOSITORY")))
}
