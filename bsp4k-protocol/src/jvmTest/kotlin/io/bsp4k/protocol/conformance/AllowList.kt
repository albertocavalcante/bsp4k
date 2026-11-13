package io.bsp4k.protocol.conformance

/** Known, justified violations: one `id  # reason` per line; `#` lines and blank lines are ignored. */
internal class AllowList(
    val reasons: Map<String, String>,
) {
    val ids: Set<String> get() = reasons.keys

    companion object {
        private val separator = Regex("\\s+#")

        fun parse(text: String): AllowList =
            AllowList(
                text
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .associate { line ->
                        val parts = line.split(separator, limit = 2)
                        parts[0].trim() to parts.getOrElse(1) { "" }.trim()
                    },
            )

        fun load(resource: String): AllowList =
            parse(checkNotNull(AllowList::class.java.getResource(resource)) { "missing $resource" }.readText())
    }
}
