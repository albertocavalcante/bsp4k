plugins {
    id("bsp4k.multiplatform")
    id("publishing-convention")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.assertions.core)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.smithy.model)
                // Method-coverage conformance inspects the public server and client APIs.
                implementation(projects.bsp4kServer)
                implementation(projects.bsp4kClient)
            }
        }
    }
}
