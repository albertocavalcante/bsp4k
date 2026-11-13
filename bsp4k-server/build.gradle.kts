plugins {
    id("bsp4k.multiplatform")
    id("publishing-convention")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.bsp4kProtocol)
                api(libs.jsonrpc4k.transport)
                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                // The framed end-to-end tests run a real client session against a real server session.
                implementation(projects.bsp4kClient)
                implementation(libs.kotlin.test)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}
