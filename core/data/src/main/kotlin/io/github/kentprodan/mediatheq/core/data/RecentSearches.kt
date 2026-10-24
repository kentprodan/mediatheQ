package io.github.kentprodan.mediatheq.core.data

/** How many recent searches are kept. */
const val MAX_RECENT_SEARCHES = 10

/**
 * These recent searches with [query] first: whitespace (newlines too) collapsed to single spaces, an older equal
 * search (ignoring case) dropped, at most [MAX_RECENT_SEARCHES]. A blank [query] changes nothing.
 */
fun List<String>.withRecentSearch(query: String): List<String> {
    val search = query.trim().replace(Regex("\\s+"), " ")
    if (search.isEmpty()) return this
    return (listOf(search) + filterNot { it.equals(search, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
}
