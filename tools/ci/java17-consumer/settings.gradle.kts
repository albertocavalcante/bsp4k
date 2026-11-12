pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven { url = uri(requireNotNull(System.getenv("BSP_FIXTURE_REPOSITORY"))) }
            }
            filter { includeGroup("io.github.albertocavalcante") }
        }
        mavenCentral()
    }
}

rootProject.name = "bsp-java17-consumer"
