package io.bsp4k.client

import io.bsp4k.protocol.BspConnectionDetails
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BspConnectionDiscoveryTest {
    @Test
    fun `workspace files come first then data directories and bad files are reported`() =
        withTempDirectory { root ->
            val workspace = root.resolve("workspace")
            val data = root.resolve("data").resolve("bsp")
            val userDetails = details("user-server")
            writeDetails(workspace.resolve(".bsp").resolve("a.json"), details("a-server"))
            writeDetails(workspace.resolve(".bsp").resolve("b.json"), details("b-server"))
            workspace.resolve(".bsp").resolve("broken.json").writeText("{ not json")
            workspace.resolve(".bsp").resolve("partial.json").writeText("""{"name":"partial","version":"1"}""")
            workspace.resolve(".bsp").resolve("empty-argv.json").writeText(
                """{"name":"x","argv":[],"version":"1","bspVersion":"2.2.0","languages":[]}""",
            )
            workspace.resolve(".bsp").resolve("notes.txt").writeText("ignored")
            workspace.resolve(".bsp").resolve("directory.json").createDirectories()
            writeDetails(data.resolve("user.json"), userDetails)

            val result = BspConnectionDiscovery(listOf(data)).discover(workspace)

            assertEquals(listOf("a-server", "b-server", "user-server"), result.connections.map { it.details.name })
            assertEquals(workspace.resolve(".bsp").resolve("a.json"), result.connections.first().file)
            assertEquals(
                setOf("broken.json", "partial.json", "empty-argv.json", "directory.json"),
                result.skipped.map { it.file.fileName.toString() }.toSet(),
            )
            assertTrue(result.skipped.all { it.reason.isNotBlank() })
            assertTrue(
                result.skipped
                    .single { it.file.fileName.toString() == "empty-argv.json" }
                    .reason
                    .contains("argv"),
            )
        }

    @Test
    fun `unknown fields are ignored and missing directories are not errors`() =
        withTempDirectory { root ->
            val workspace = root.resolve("workspace")
            workspace.resolve(".bsp").resolve("extra.json").also { it.parent.createDirectories() }.writeText(
                """{"name":"x","argv":["x"],"version":"1","bspVersion":"2.2.0","languages":["kotlin"],"future":{"a":1}}""",
            )

            val result = BspConnectionDiscovery(listOf(root.resolve("absent"))).discover(workspace)

            assertEquals(listOf("x"), result.connections.map { it.details.name })
            assertEquals(emptyList(), result.skipped)
            assertEquals(DiscoveryResult(emptyList(), emptyList()), BspConnectionDiscovery(emptyList()).discover(root.resolve("none")))
        }

    @Test
    fun `linux data directories follow the specification`() {
        // XDG_DATA_HOME must be absolute on the host file system: "/xdg" has no drive on Windows.
        val xdg = Path.of("").toAbsolutePath().root.resolve("xdg")
        assertEquals(
            listOf(
                xdg.resolve("bsp"),
                Path.of("/home/user/.local/share/bsp"),
                Path.of("/usr/local/share/bsp"),
                Path.of("/usr/share/bsp"),
            ),
            dataDirectoriesFor("Linux", mapOf("XDG_DATA_HOME" to xdg.toString()), "/home/user"),
        )
        assertEquals(
            listOf(Path.of("/home/user/.local/share/bsp"), Path.of("/usr/local/share/bsp"), Path.of("/usr/share/bsp")),
            dataDirectoriesFor("Linux", mapOf("XDG_DATA_HOME" to "relative"), "/home/user"),
        )
    }

    @Test
    fun `macOS and Windows data directories follow the specification`() {
        assertEquals(
            listOf(Path.of("/Users/u/Library/Application Support/bsp"), Path.of("/Library/Application Support/bsp")),
            dataDirectoriesFor("Mac OS X", emptyMap(), "/Users/u"),
        )
        assertEquals(
            listOf(Path.of("/local").resolve("bsp"), Path.of("/program").resolve("bsp")),
            dataDirectoriesFor("Windows 11", mapOf("LOCALAPPDATA" to "/local", "PROGRAMDATA" to "/program"), null),
        )
        assertEquals(emptyList(), dataDirectoriesFor("Windows 11", emptyMap(), null))
    }
}

internal fun details(
    name: String,
    argv: List<String> = listOf("server", "--stdio"),
): BspConnectionDetails =
    BspConnectionDetails(name = name, argv = argv, version = "1.0", bspVersion = "2.2.0", languages = listOf("kotlin"))

internal fun writeDetails(
    file: Path,
    details: BspConnectionDetails,
) {
    file.parent.createDirectories()
    file.writeText(Json.encodeToString(BspConnectionDetails.serializer(), details))
}

internal fun withTempDirectory(block: (Path) -> Unit) {
    val directory = Files.createTempDirectory("bsp-discovery-")
    try {
        block(directory)
    } finally {
        directory.toFile().deleteRecursively()
    }
}
