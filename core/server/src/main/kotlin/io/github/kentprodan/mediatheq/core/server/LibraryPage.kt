package io.github.kentprodan.mediatheq.core.server

/** One page of a paged server listing; [totalCount] is the size of the whole listing. */
data class LibraryPage<T>(
    val items: List<T>,
    val totalCount: Int,
)
