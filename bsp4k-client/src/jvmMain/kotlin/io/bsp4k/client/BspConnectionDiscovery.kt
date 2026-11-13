package io.bsp4k.client

import io.bsp4k.protocol.BspConnectionDetails
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val MAX_CONNECTION_FILE_BYTES: Long = 1_048_576
private const val BSP_DIRECTORY = ".bsp"
private const val DATA_SUBDIRECTORY = "bsp"
private const val JSON_SUFFIX = ".json"

private val lenientJson = Json { ignoreUnknownKeys = true }

/** A valid connection file found by [BspConnectionDiscovery]. */
public data class DiscoveredConnection(
    val file: Path,
    val details: BspConnectionDetails,
)

/** A connection file that [BspConnectionDiscovery] ignored, and why. */
public data class SkippedConnectionFile(
    val file: Path,
    val reason: String,
)

/** Outcome of a discovery run: valid connections in priority order, plus every file that was skipped. */
public data class DiscoveryResult(
    val connections: List<DiscoveredConnection>,
    val skipped: List<SkippedConnectionFile>,
)

/**
 * Finds BSP server connection files following the BSP server discovery rules: first the `.bsp` directory of
 * the workspace root, then the `bsp` directory of each user and system data directory.
 *
 * Every JSON file in those directories is a candidate. A file that is unreadable, larger than 1 MiB, not valid
 * JSON or missing required fields is skipped and reported in [DiscoveryResult.skipped] instead of failing the run;
 * unknown fields are ignored. Missing directories are not an error. Connections keep the search order, and files
 * within one directory are ordered by name, so the first entry is the preferred server.
 *
 * Discovery reads the file system synchronously; call it from a dispatcher that may block.
 *
 * @param dataDirectories the `bsp` directories searched after the workspace, in priority order.
 */
public class BspConnectionDiscovery(
    private val dataDirectories: List<Path> = defaultDataDirectories(),
) {
    /** Searches [workspaceRoot] and then the configured data directories. */
    public fun discover(workspaceRoot: Path): DiscoveryResult {
        val connections = mutableListOf<DiscoveredConnection>()
        val skipped = mutableListOf<SkippedConnectionFile>()
        val directories = (listOf(workspaceRoot.resolve(BSP_DIRECTORY)) + dataDirectories).map { it.normalize() }.distinct()
        for (directory in directories) {
            for (file in connectionFiles(directory, skipped)) {
                when (val outcome = readConnectionFile(file)) {
                    is FileOutcome.Valid -> connections += DiscoveredConnection(file, outcome.details)
                    is FileOutcome.Invalid -> skipped += SkippedConnectionFile(file, outcome.reason)
                }
            }
        }
        return DiscoveryResult(connections, skipped)
    }

    public companion object {
        /**
         * The `bsp` directories for the running platform, in priority order: on Linux `XDG_DATA_HOME`,
         * `~/.local/share`, `/usr/local/share` and `/usr/share`; on macOS the user and system
         * `Library/Application Support`; on Windows `LOCALAPPDATA` and `PROGRAMDATA`.
         */
        public fun defaultDataDirectories(): List<Path> =
            dataDirectoriesFor(System.getProperty("os.name").orEmpty(), System.getenv(), System.getProperty("user.home"))
    }
}

private sealed interface FileOutcome {
    data class Valid(
        val details: BspConnectionDetails,
    ) : FileOutcome

    data class Invalid(
        val reason: String,
    ) : FileOutcome
}

private fun connectionFiles(
    directory: Path,
    skipped: MutableList<SkippedConnectionFile>,
): List<Path> {
    if (!Files.isDirectory(directory)) return emptyList()
    return try {
        Files.list(directory).use { entries ->
            entries
                .filter { it.fileName.toString().endsWith(JSON_SUFFIX) }
                .sorted()
                .toList()
        }
    } catch (failure: IOException) {
        skipped += SkippedConnectionFile(directory, "cannot list directory: ${failure.message ?: failure::class.simpleName}")
        emptyList()
    }
}

private fun readConnectionFile(file: Path): FileOutcome =
    try {
        FileOutcome.Valid(validated(lenientJson.decodeFromString(BspConnectionDetails.serializer(), readBounded(file))))
    } catch (failure: IOException) {
        FileOutcome.Invalid("cannot read file: ${failure.message ?: failure::class.simpleName}")
    } catch (failure: IllegalArgumentException) {
        FileOutcome.Invalid("invalid connection details: ${failure.message}")
    }

private fun readBounded(file: Path): String {
    if (!Files.isRegularFile(file)) throw IOException("not a regular file")
    if (Files.size(file) > MAX_CONNECTION_FILE_BYTES) throw IOException("larger than $MAX_CONNECTION_FILE_BYTES bytes")
    return Files.readString(file)
}

private fun validated(details: BspConnectionDetails): BspConnectionDetails {
    require(details.name.isNotBlank()) { "name is blank" }
    require(details.argv.isNotEmpty() && details.argv.first().isNotBlank()) { "argv has no command" }
    require(details.version.isNotBlank()) { "version is blank" }
    require(details.bspVersion.isNotBlank()) { "bspVersion is blank" }
    return details
}

internal fun dataDirectoriesFor(
    osName: String,
    environment: Map<String, String>,
    home: String?,
): List<Path> {
    val os = osName.lowercase()
    val homePath = pathOrNull(home)
    val directories =
        when {
            "windows" in os -> listOf(pathOrNull(environment["LOCALAPPDATA"]), pathOrNull(environment["PROGRAMDATA"]))
            "mac" in os ->
                listOf(
                    homePath?.resolve("Library")?.resolve("Application Support"),
                    pathOrNull("/Library/Application Support"),
                )
            else ->
                listOf(
                    pathOrNull(environment["XDG_DATA_HOME"])?.takeIf { it.isAbsolute },
                    homePath?.resolve(".local")?.resolve("share"),
                    pathOrNull("/usr/local/share"),
                    pathOrNull("/usr/share"),
                )
        }
    return directories.filterNotNull().map { it.resolve(DATA_SUBDIRECTORY) }.distinct()
}

private fun pathOrNull(value: String?): Path? =
    if (value.isNullOrBlank()) {
        null
    } else {
        try {
            Path.of(value)
        } catch (ignored: InvalidPathException) {
            null
        }
    }
