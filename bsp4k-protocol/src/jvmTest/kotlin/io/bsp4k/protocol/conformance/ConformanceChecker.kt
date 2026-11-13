package io.bsp4k.protocol.conformance

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.EnumShape
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.JsonNameTrait
import software.amazon.smithy.model.traits.MixinTrait
import software.amazon.smithy.model.traits.RequiredTrait

private val JSON_REQUEST: ShapeId = ShapeId.from("traits#jsonRequest")
private val JSON_NOTIFICATION: ShapeId = ShapeId.from("traits#jsonNotification")
private val DATA_KIND: ShapeId = ShapeId.from("traits#dataKind")
private val ENUM_KIND: ShapeId = ShapeId.from("traits#enumKind")

/** The JSON-RPC method of an operation, or null when the operation is neither a request nor a notification. */
internal fun methodOf(operation: OperationShape): String? =
    listOf(JSON_REQUEST, JSON_NOTIFICATION).firstNotNullOfOrNull { trait ->
        operation.findTrait(trait).map { it.toNode().expectStringNode().value }.orElse(null)
    }

/** Method string to operation for every JSON-RPC operation in [model]. */
internal fun specOperations(model: Model): Map<String, OperationShape> =
    model.operationShapes.mapNotNull { operation -> methodOf(operation)?.let { it to operation } }.toMap()

/**
 * Compares bsp4k against a Smithy model and returns one stable id per difference. Ids are of the form
 * `rule:shapeId` or `rule:shapeId$member`, and are what the allow-list names.
 */
internal class ConformanceChecker(
    private val model: Model,
    private val bind: (Shape) -> Binding?,
    private val methods: Set<String>,
    private val dataKinds: Set<String>,
) {
    private val violations = sortedSetOf<String>()

    fun run(): Set<String> {
        model.structureShapes
            .filter { inScope(it) && !it.hasTrait(MixinTrait::class.java) }
            .forEach(::checkStructure)
        val enums: List<Shape> = model.enumShapes.toList() + model.intEnumShapes.toList()
        enums.filter(::inScope).forEach(::checkEnum)
        model.unionShapes.filter(::inScope).forEach(::checkUnion)
        checkMethods()
        checkDataKinds()
        return violations
    }

    private fun inScope(shape: Shape): Boolean = shape.id.namespace == "bsp" || shape.id.namespace.startsWith("bsp.")

    private fun report(
        rule: String,
        shape: Shape,
        member: String? = null,
    ) {
        violations += if (member == null) "$rule:${shape.id}" else "$rule:${shape.id}${'$'}$member"
    }

    private fun checkStructure(shape: StructureShape) {
        val binding = bind(shape) as? Binding.Struct
        if (binding == null) {
            report("shape-missing", shape)
            return
        }
        val descriptor = binding.serializer.descriptor
        val actual = (0 until descriptor.elementsCount).associateBy(descriptor::getElementName)
        val expected = expectedMembers(shape)
        (expected.keys - actual.keys).forEach { report("member-missing", shape, it) }
        (actual.keys - expected.keys).forEach { report("member-extra", shape, it) }
        expected.forEach { (name, required) ->
            actual[name]?.let { checkRequiredness(shape, name, required, descriptor, it) }
        }
        if ("data" in expected && !binding.carrier) report("carrier", shape)
    }

    private fun checkRequiredness(
        shape: Shape,
        name: String,
        required: Boolean,
        descriptor: SerialDescriptor,
        index: Int,
    ) {
        val optional = descriptor.isElementOptional(index)
        when {
            required && optional -> report("member-required-has-default", shape, name)
            required && descriptor.getElementDescriptor(index).isNullable ->
                report("member-required-nullable", shape, name)
            !required && !optional -> report("member-optional-required", shape, name)
        }
    }

    /** JSON member name to required-ness, including mixin members and the synthetic `dataKind`. */
    private fun expectedMembers(shape: StructureShape): Map<String, Boolean> {
        val members =
            shape.allMembers.values.associate { member ->
                val name = member.getTrait(JsonNameTrait::class.java).map { it.value }.orElse(member.memberName)
                name to member.hasTrait(RequiredTrait::class.java)
            }
        return if ("data" in members && "dataKind" !in members) members + ("dataKind" to false) else members
    }

    private fun checkEnum(shape: Shape) {
        val closed = enumKind(shape) != "open"
        val (bindingClosed, actual, rule) =
            when (val binding = bind(shape)) {
                is Binding.ClosedEnum -> Triple(true, binding.wireValues, "enum-values")
                is Binding.OpenEnum -> Triple(false, binding.values, "open-enum-values")
                else -> {
                    report("shape-missing", shape)
                    return
                }
            }
        when {
            bindingClosed != closed -> report("enum-kind", shape)
            actual != enumValues(shape) -> report(rule, shape)
        }
    }

    private fun enumKind(shape: Shape): String? = shape.findTrait(ENUM_KIND).map { it.toNode().expectStringNode().value }.orElse(null)

    private fun enumValues(shape: Shape): Set<JsonElement> =
        when (shape) {
            is EnumShape -> shape.enumValues.values.map { JsonPrimitive(it) }
            is IntEnumShape -> shape.enumValues.values.map { JsonPrimitive(it) }
            else -> emptyList()
        }.toSet()

    private fun checkUnion(shape: Shape) {
        if (bind(shape) !is Binding.Union) report("shape-missing", shape)
    }

    private fun checkMethods() {
        val spec = specOperations(model).keys
        (spec - methods).forEach { violations += "method-missing:$it" }
        (methods - spec).forEach { violations += "method-extra:$it" }
    }

    private fun checkDataKinds() {
        model.shapes().toList().forEach { shape ->
            shape.findTrait(DATA_KIND).ifPresent { trait ->
                val kind =
                    trait
                        .toNode()
                        .expectObjectNode()
                        .expectStringMember("kind")
                        .value
                if (kind !in dataKinds) violations += "datakind-missing:$kind:${shape.id}"
            }
        }
    }
}
