package io.bsp4k.protocol.conformance

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Mechanical G3.1 gate: bsp4k must match the vendored BSP 2.2.0 Smithy model. */
class SmithyConformanceTest {
    @Test
    fun `vendored spec files match their pinned checksums`() {
        val pins = BspSpec.pins()
        withClue("PINS.txt must list exactly the vendored spec files") {
            pins.keys shouldBe BspSpec.pinnedFiles.toSet()
        }
        BspSpec.pinnedFiles.forEach { name ->
            withClue("sha256 of $name") { BspSpec.sha256(name) shouldBe pins.getValue(name) }
        }
    }

    @Test
    fun `the vendored model declares the expected operations`() {
        specOperations(BspSpec.model).size shouldBe EXPECTED_OPERATIONS
    }

    @Test
    fun `bsp4k protocol conforms to the BSP 2 2 0 Smithy model`() {
        val violations = currentViolations()
        val allowed = AllowList.load(ALLOW_LIST).ids
        val unexpected = violations - allowed
        val stale = allowed - violations
        withClue(
            "New violations (fix them or allow-list them with a reason):\n${unexpected.joinToString("\n")}\n" +
                "Stale allow-list entries (delete them):\n${stale.joinToString("\n")}\n",
        ) {
            unexpected shouldBe emptySet<String>()
            stale shouldBe emptySet<String>()
        }
    }

    @Test
    fun `every allow-list entry states a reason`() {
        val withoutReason = AllowList.load(ALLOW_LIST).reasons.filterValues { it.isEmpty() }.keys
        withClue("Add a reason after the id: id  # reason") { withoutReason shouldBe emptySet<String>() }
    }

    private fun currentViolations(): Set<String> =
        ConformanceChecker(
            model = BspSpec.model,
            bind = KotlinBindings::resolve,
            methods = KotlinBindings.methods(),
            dataKinds = KotlinBindings.dataKinds(),
        ).run()

    private companion object {
        const val ALLOW_LIST = "/conformance/allowlist.txt"
        const val EXPECTED_OPERATIONS = 41
    }
}
