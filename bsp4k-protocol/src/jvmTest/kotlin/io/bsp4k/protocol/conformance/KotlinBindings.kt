package io.bsp4k.protocol.conformance

import io.bsp4k.protocol.BspDataCarrier
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CargoBuildTarget
import io.bsp4k.protocol.CppBuildTarget
import io.bsp4k.protocol.JvmDataKinds
import io.bsp4k.protocol.MavenDataKinds
import io.bsp4k.protocol.PythonBuildTarget
import io.bsp4k.protocol.SbtDataKinds
import io.bsp4k.protocol.ScalaDataKinds
import io.bsp4k.protocol.TaskFinishDataKind
import io.bsp4k.protocol.TaskStartDataKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializerOrNull
import software.amazon.smithy.model.shapes.EnumShape
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.UnionShape
import java.lang.reflect.Modifier

/** How a Smithy shape is represented by bsp4k. */
internal sealed interface Binding {
    class Struct(
        val serializer: KSerializer<*>,
        val carrier: Boolean,
    ) : Binding

    class ClosedEnum(
        val wireValues: Set<JsonElement>,
    ) : Binding

    class OpenEnum(
        val values: Set<JsonElement>,
    ) : Binding

    class Union(
        val serializer: KSerializer<*>,
    ) : Binding
}

/** Finds the bsp4k representation of Smithy shapes and the protocol constants, by reflection. */
internal object KotlinBindings {
    private const val PACKAGE = "io.bsp4k.protocol"

    /** Smithy shapes that bsp4k merges into one Kotlin type (run and test environments share a shape). */
    private val renames =
        mapOf(
            "bsp.jvm#JvmTestEnvironmentParams" to "JvmEnvironmentParams",
            "bsp.jvm#JvmRunEnvironmentParams" to "JvmEnvironmentParams",
            "bsp.jvm#JvmTestEnvironmentResult" to "JvmEnvironmentResult",
            "bsp.jvm#JvmRunEnvironmentResult" to "JvmEnvironmentResult",
        )

    private val json = Json

    fun resolve(shape: Shape): Binding? {
        val name = renames[shape.id.toString()] ?: shape.id.name
        val cls = runCatching { Class.forName("$PACKAGE.$name") }.getOrNull() ?: return null
        val serializer = serializerOrNull(cls)
        return when {
            serializer == null -> if (shape is EnumShape || shape is IntEnumShape) openEnum(cls) else null
            cls.isEnum -> closedEnum(cls, serializer)
            shape is UnionShape -> Binding.Union(serializer)
            else -> Binding.Struct(serializer, BspDataCarrier::class.java.isAssignableFrom(cls))
        }
    }

    /** Every `BspMethods` constant, including `$/cancelRequest`. */
    fun methods(): Set<String> = staticStrings(BspMethods::class.java)

    /** Every `dataKind` string bsp4k defines: typed kinds, the per-extension constants and `KIND` companions. */
    fun dataKinds(): Set<String> =
        buildSet {
            listOf(
                JvmDataKinds::class.java,
                ScalaDataKinds::class.java,
                MavenDataKinds::class.java,
                SbtDataKinds::class.java,
                CargoBuildTarget::class.java,
                CppBuildTarget::class.java,
                PythonBuildTarget::class.java,
            ).forEach { addAll(staticStrings(it)) }
            TaskStartDataKind.entries.forEach { add(it.kind) }
            TaskFinishDataKind.entries.forEach { add(it.kind) }
        }

    private fun closedEnum(
        cls: Class<*>,
        serializer: KSerializer<Any>,
    ): Binding =
        Binding.ClosedEnum(
            cls.enumConstants.map { json.encodeToJsonElement(serializer, requireNotNull(it)) }.toSet(),
        )

    private fun openEnum(cls: Class<*>): Binding =
        Binding.OpenEnum(
            cls.declaredFields
                .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
                .mapNotNull { primitive(it.get(null)) }
                .toSet(),
        )

    private fun primitive(value: Any?): JsonElement? =
        when (value) {
            is String -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            else -> null
        }

    private fun staticStrings(cls: Class<*>): Set<String> =
        cls.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
            .filter { it.type == String::class.java }
            .mapNotNull { it.get(null) as? String }
            .toSet()
}
