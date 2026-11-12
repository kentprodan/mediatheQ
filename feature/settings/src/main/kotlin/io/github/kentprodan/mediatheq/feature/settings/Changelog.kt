package io.github.kentprodan.mediatheq.feature.settings

/** One version of the changelog (#151): its name ("0.2.0" or "Unreleased"), date and sections, as the file has them. */
data class ChangelogVersion(
    val name: String,
    /** The release date as written ("2026-11-12"); `null` while unreleased. */
    val date: String?,
    val sections: List<ChangelogSection>,
) {
    val unreleased: Boolean get() = name.equals("Unreleased", ignoreCase = true)
}

/** "Added", "Changed", "Fixed", ... and its items. */
data class ChangelogSection(
    val title: String,
    val items: List<String>,
)

/**
 * The versions of a Keep a Changelog file, in the file's order (newest first): `## [0.2.0] - 2026-11-12`, then
 * `### Added` sections of `- item` lines; an indented line (also a nested item) continues the item above. Anything
 * else (the title, prose, link references, an item outside a section) is skipped.
 */
fun parseChangelog(text: String): List<ChangelogVersion> {
    val versions = mutableListOf<Triple<String, String?, MutableList<Pair<String, MutableList<String>>>>>()
    for (line in text.lines()) {
        val sections = versions.lastOrNull()?.third
        val items = sections?.lastOrNull()?.second
        when {
            // Every `## ` heading starts a version, so its sections never land under the one above.
            line.startsWith("## ") -> {
                val heading = line.removePrefix("## ").trim()
                val version = VERSION.matchEntire(heading)
                versions +=
                    if (version != null) {
                        Triple(version.groupValues[1], version.groupValues[2].ifEmpty { null }, mutableListOf())
                    } else {
                        Triple(heading, null, mutableListOf())
                    }
            }

            line.startsWith("### ") && sections != null -> {
                sections += line.removePrefix("### ").trim() to mutableListOf()
            }

            ITEM.matches(line) && items != null -> {
                items += line.trim().drop(1).trim()
            }

            line.startsWith("  ") && line.isNotBlank() && !items.isNullOrEmpty() -> {
                items[items.lastIndex] = items.last() + " " + line.trim()
            }
        }
    }
    return versions.map { (name, date, sections) ->
        ChangelogVersion(name, date, sections.map { (title, items) -> ChangelogSection(title, items.toList()) })
    }
}

// "[0.2.0] - 2026-11-12", "0.2.0", "[1.0.0](link) - date", "[0.0.5] - 2014-02-25 [YANKED]".
private val VERSION = Regex("""\[?([^\]\s(]+)]?(?:\([^)]*\))?(?:\s+-\s+(\S+)(?:\s.*)?)?""")
private val ITEM = Regex("""[-*]\s+\S.*""")
