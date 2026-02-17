plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.bsp4k.example.ExampleBuildServerKt")
}

dependencies {
    implementation(projects.bsp4kServer)
}
