package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlin.test.Test

class ConnectionDetailsSerializationTest {
    @Test
    fun `server discovery sample decodes`() {
        val sample =
            """
            {
              "name": "sbt",
              "argv": ["java", "-Xms100m", "-Xmx100m", "-jar", "/path/to/sbt-launch.jar", "-bsp"],
              "version": "1.5.0",
              "bspVersion": "2.0.0",
              "languages": ["scala", "java"]
            }
            """.trimIndent()
        wireJson.decodeFromString(BspConnectionDetails.serializer(), sample) shouldBe
            BspConnectionDetails(
                name = "sbt",
                argv = listOf("java", "-Xms100m", "-Xmx100m", "-jar", "/path/to/sbt-launch.jar", "-bsp"),
                version = "1.5.0",
                bspVersion = "2.0.0",
                languages = listOf("scala", "java"),
            )
    }

    @Test
    fun `connection details encode every required member`() {
        val details =
            BspConnectionDetails(
                name = "srv",
                argv = emptyList(),
                version = "1",
                bspVersion = "2.2.0",
                languages = emptyList(),
            )
        wireJson.encodeToString(BspConnectionDetails.serializer(), details) shouldBe
            """{"name":"srv","argv":[],"version":"1","bspVersion":"2.2.0","languages":[]}"""
    }

    @Test
    fun `connection details without bspVersion fail to decode`() {
        shouldThrow<SerializationException> {
            wireJson.decodeFromString(
                BspConnectionDetails.serializer(),
                """{"name":"s","argv":[],"version":"1","languages":[]}""",
            )
        }
    }
}
