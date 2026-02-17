plugins {
    id("bsp4k.multiplatform")
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
