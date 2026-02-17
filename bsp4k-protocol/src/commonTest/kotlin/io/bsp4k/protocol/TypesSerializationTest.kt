package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class TypesSerializationTest {
    private val json = Json

    @Test
    fun `BuildTargetIdentifier roundtrips`() {
        val original = BuildTargetIdentifier(uri = "file:///project/main")
        val encoded = json.encodeToString(BuildTargetIdentifier.serializer(), original)
        val decoded = json.decodeFromString(BuildTargetIdentifier.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `BuildTargetIdentifier encodes to expected JSON`() {
        val id = BuildTargetIdentifier(uri = "file:///project/main")
        val encoded = json.encodeToString(BuildTargetIdentifier.serializer(), id)
        encoded shouldBe """{"uri":"file:///project/main"}"""
    }

    @Test
    fun `TextDocumentIdentifier roundtrips`() {
        val original = TextDocumentIdentifier(uri = "file:///src/Main.kt")
        val encoded = json.encodeToString(TextDocumentIdentifier.serializer(), original)
        val decoded = json.decodeFromString(TextDocumentIdentifier.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TextDocumentIdentifier encodes to expected JSON`() {
        val id = TextDocumentIdentifier(uri = "file:///src/Main.kt")
        val encoded = json.encodeToString(TextDocumentIdentifier.serializer(), id)
        encoded shouldBe """{"uri":"file:///src/Main.kt"}"""
    }
}
