package io.bsp4k.protocol.conformance

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

/** Mechanical G3.4 gate: every BSP method has a server and a client surface in bsp4k. */
class MethodCoverageTest {
    private val operations = specOperations(BspSpec.model)

    @Test
    fun `the endpoint table covers exactly the methods of the model`() {
        withClue("Methods in the model without a table entry, and table entries without a method") {
            endpointTable.keys shouldBe operations.keys
        }
    }

    @Test
    fun `table directions match the services of the model`() {
        val clientOperations = serviceOperations("bsp#BuildClient")
        val cancelOperations = serviceOperations("bsp.cancel#CancelExtension")
        val derived =
            operations.mapValues { (_, operation) ->
                when (operation.id) {
                    in clientOperations -> Direction.ServerToClient
                    in cancelOperations -> Direction.Bidirectional
                    else -> Direction.ClientToServer
                }
            }
        endpointTable.mapValues { it.value.direction } shouldBe derived
    }

    @Test
    fun `every method has the server and client surfaces the table names`() {
        val missing =
            endpointTable.flatMap { (method, endpoint) ->
                listOfNotNull(endpoint.server, endpoint.client)
                    .filterNot(::exists)
                    .map { "$method -> ${it.className}#${it.function}" }
            }
        withClue("Surfaces that do not exist") { missing shouldBe emptyList<String>() }
    }

    @Test
    fun `only the cancel notification is handled below the bsp4k API`() {
        endpointTable.filterValues { it.server == null || it.client == null }.keys shouldBe setOf("\$/cancelRequest")
    }

    private fun serviceOperations(id: String): Set<ShapeId> =
        BspSpec.model
            .expectShape(ShapeId.from(id), ServiceShape::class.java)
            .operations
            .toSet()

    private fun exists(surface: Surface): Boolean =
        runCatching { Class.forName(surface.className) }
            .getOrNull()
            ?.methods
            ?.any { it.name == surface.function } == true
}
