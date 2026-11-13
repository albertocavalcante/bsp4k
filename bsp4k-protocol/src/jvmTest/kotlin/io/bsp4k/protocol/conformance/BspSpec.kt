package io.bsp4k.protocol.conformance

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.validation.Severity
import java.net.URL
import java.security.MessageDigest

/** The vendored BSP 2.2.0 Smithy files (see `SOURCE.md` next to them) and the model assembled from them. */
internal object BspSpec {
    private const val ROOT = "/bsp/2.2.0"

    private val extensionNames =
        listOf("cancel", "cargo", "cpp", "java", "jvm", "maven", "python", "rust", "sbt", "scala")

    /** Files fed to the Smithy assembler, trait definitions first. */
    val modelFiles: List<String> =
        listOf("traits/traits.smithy", "bsp.smithy", "tags.smithy") + extensionNames.map { "extensions/$it.smithy" }

    /** Every vendored file covered by `PINS.txt`. */
    val pinnedFiles: List<String> = modelFiles + "version"

    val model: Model by lazy { assemble() }

    fun resource(name: String): URL = checkNotNull(BspSpec::class.java.getResource("$ROOT/$name")) { "missing vendored spec file $name" }

    fun sha256(name: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(resource(name).readBytes())
            .joinToString("") { "%02x".format(it) }

    /** Parses `PINS.txt` into path to sha256. */
    fun pins(): Map<String, String> =
        resource("PINS.txt")
            .readText()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { it.substringAfter("  ") to it.substringBefore("  ") }

    private fun assemble(): Model {
        val assembler = Model.assembler()
        modelFiles.forEach { assembler.addImport(resource(it)) }
        val result = assembler.assemble()
        val errors = result.validationEvents.filter { it.severity == Severity.ERROR }
        check(errors.isEmpty()) { "The vendored BSP model does not validate:\n" + errors.joinToString("\n") }
        return result.result.orElseThrow()
    }
}
