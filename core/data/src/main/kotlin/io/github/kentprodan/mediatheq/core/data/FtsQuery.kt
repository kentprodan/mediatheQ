package io.github.kentprodan.mediatheq.core.data

/**
 * Turns typed text into a safe FTS5 expression: each word becomes a quoted string (so `"`, `-`, `:`, `/`, `NOT` are
 * plain text, never syntax), and the last word is a prefix. `null` when there is nothing to search for.
 */
internal fun ftsMatchExpression(input: String): String? {
    val words = input.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"" } + "*"
}
