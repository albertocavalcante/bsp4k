import org.gradle.api.publish.PublishingExtension

settingsEvaluated {
    val fixtureRepositories = dependencyResolutionManagement.repositories
    fixtureRepositories.exclusiveContent {
        forRepository {
            fixtureRepositories.maven { url = uri(requireNotNull(System.getenv("BSP_FIXTURE_REPOSITORY"))) }
        }
        filter { includeGroup("io.github.albertocavalcante") }
    }
}

allprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "Fixture"
                url = uri(requireNotNull(System.getenv("BSP_FIXTURE_REPOSITORY")))
            }
        }
    }
}
