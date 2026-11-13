package io.bsp4k.protocol.conformance

import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import kotlin.test.Test

/** Seeded-defect tests: each rule of the checker must fail on a deliberately wrong binding. */
class ConformanceCheckerTest {
    private val thing = "bsp.fixture#Thing"
    private val level = "bsp.fixture#Level"
    private val dollar = '$'

    private val fixture: Model =
        Model
            .assembler()
            .addImport(BspSpec.resource("traits/traits.smithy"))
            .addUnparsedModel(
                "fixture.smithy",
                """
                ${dollar}version: "2"
                namespace bsp.fixture
                use traits#enumKind
                use traits#jsonRequest
                structure Thing {
                    @required
                    name: String
                    flag: Boolean = false
                    note: String
                }
                @enumKind("closed")
                intEnum Level {
                    LOW = 1
                    HIGH = 2
                }
                @jsonRequest("fixture/get")
                operation Get {
                    input: Thing
                }
                """.trimIndent(),
            ).assemble()
            .unwrap()

    @Serializable
    private data class GoodThing(
        val name: String,
        val flag: Boolean = false,
        val note: String? = null,
    )

    @Serializable
    private data class DefaultedName(
        val name: String = "",
        val flag: Boolean = false,
        val note: String? = null,
    )

    @Serializable
    private data class RenamedName(
        @SerialName("title") val name: String,
        val flag: Boolean = false,
        val note: String? = null,
    )

    @Serializable
    private data class RequiredNote(
        val name: String,
        val flag: Boolean = false,
        val note: String,
    )

    @Serializable
    private data class NullableName(
        val name: String?,
        val flag: Boolean = false,
        val note: String? = null,
    )

    private fun check(
        struct: KSerializer<*>? = GoodThing.serializer(),
        levelValues: Set<Int> = setOf(1, 2),
        methods: Set<String> = setOf("fixture/get"),
    ): Set<String> {
        val bind: (Shape) -> Binding? = { shape ->
            when (shape.id.toString()) {
                thing -> struct?.let { Binding.Struct(it, carrier = false) }
                level -> Binding.ClosedEnum(levelValues.map { JsonPrimitive(it) }.toSet())
                else -> null
            }
        }
        return ConformanceChecker(fixture, bind, methods, dataKinds = emptySet()).run()
    }

    @Test
    fun `a faithful binding has no violations`() {
        check() shouldBe emptySet<String>()
    }

    @Test
    fun `a required member with a Kotlin default is reported`() {
        check(DefaultedName.serializer()) shouldBe setOf("member-required-has-default:$thing${dollar}name")
    }

    @Test
    fun `a required nullable member is reported`() {
        check(NullableName.serializer()) shouldBe setOf("member-required-nullable:$thing${dollar}name")
    }

    @Test
    fun `an optional member without a Kotlin default is reported`() {
        check(RequiredNote.serializer()) shouldBe setOf("member-optional-required:$thing${dollar}note")
    }

    @Test
    fun `a renamed member is reported as missing and extra`() {
        check(RenamedName.serializer()) shouldBe
            setOf("member-missing:$thing${dollar}name", "member-extra:$thing${dollar}title")
    }

    @Test
    fun `a missing structure is reported`() {
        check(struct = null) shouldBe setOf("shape-missing:$thing")
    }

    @Test
    fun `wrong enum values are reported`() {
        check(levelValues = setOf(1)) shouldBe setOf("enum-values:$level")
    }

    @Test
    fun `method differences are reported in both directions`() {
        check(methods = setOf("fixture/other")) shouldBe
            setOf("method-missing:fixture/get", "method-extra:fixture/other")
    }

    @Test
    fun `allow-list lines parse into ids and reasons`() {
        val member = "member-extra:bsp#Y${dollar}z"
        val list = AllowList.parse("# comment\n\nshape-missing:bsp#X  # not modeled yet\n$member\n")
        list.reasons shouldBe mapOf("shape-missing:bsp#X" to "not modeled yet", member to "")
    }
}
