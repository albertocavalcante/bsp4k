package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CapabilitiesSerializationTest {
    @Test
    fun `empty server capabilities encode as empty object`() {
        wireJson.encodeToString(BuildServerCapabilities.serializer(), BuildServerCapabilities()) shouldBe "{}"
    }

    @Test
    fun `empty object decodes to all-false server capabilities`() {
        wireJson.decodeFromString(BuildServerCapabilities.serializer(), "{}") shouldBe BuildServerCapabilities()
    }

    @Test
    fun `server capabilities encode every extension member under their spec names`() {
        val caps =
            BuildServerCapabilities(
                buildTargetDestinationsProvider = true,
                cargoFeaturesProvider = true,
                jvmCompileClasspathProvider = true,
                canReload = true,
            )
        wireJson.encodeToString(BuildServerCapabilities.serializer(), caps) shouldBe
            """{"buildTargetDestinationsProvider":true,"cargoFeaturesProvider":true,""" +
            """"canReload":true,"jvmCompileClasspathProvider":true}"""
    }

    @Test
    fun `server capabilities decode all boolean members`() {
        val text =
            """{"buildTargetDestinationsProvider":true,"inverseSourcesProvider":true,""" +
                """"dependencySourcesProvider":true,"dependencyModulesProvider":true,"resourcesProvider":true,""" +
                """"outputPathsProvider":true,"buildTargetChangedProvider":true,"jvmRunEnvironmentProvider":true,""" +
                """"jvmTestEnvironmentProvider":true,"cargoFeaturesProvider":true,"canReload":true,""" +
                """"jvmCompileClasspathProvider":true}"""
        wireJson.decodeFromString(BuildServerCapabilities.serializer(), text) shouldBe
            BuildServerCapabilities(
                buildTargetDestinationsProvider = true,
                inverseSourcesProvider = true,
                dependencySourcesProvider = true,
                dependencyModulesProvider = true,
                resourcesProvider = true,
                outputPathsProvider = true,
                buildTargetChangedProvider = true,
                jvmRunEnvironmentProvider = true,
                jvmTestEnvironmentProvider = true,
                cargoFeaturesProvider = true,
                canReload = true,
                jvmCompileClasspathProvider = true,
            )
    }

    @Test
    fun `client capabilities always encode required languageIds and omit false receivers`() {
        val caps = BuildClientCapabilities(languageIds = emptyList())
        wireJson.encodeToString(BuildClientCapabilities.serializer(), caps) shouldBe """{"languageIds":[]}"""
    }

    @Test
    fun `client capabilities encode and decode receivers`() {
        val caps =
            BuildClientCapabilities(
                languageIds = listOf("kotlin"),
                buildTargetDestinationsReceiver = true,
                jvmCompileClasspathReceiver = true,
            )
        val text =
            """{"languageIds":["kotlin"],"buildTargetDestinationsReceiver":true,""" +
                """"jvmCompileClasspathReceiver":true}"""
        wireJson.encodeToString(BuildClientCapabilities.serializer(), caps) shouldBe text
        wireJson.decodeFromString(BuildClientCapabilities.serializer(), text) shouldBe caps
    }
}
