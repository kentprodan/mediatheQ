package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album

/** One row of the Library's Years list (#129): a year and its albums; [year] `null` = Unknown year. */
data class YearAlbums(
    val year: Int?,
    val albums: List<Album>,
)

/**
 * The years of [albums] (the Library's albums: only downloads while offline, ADR 0021), newest first, Unknown year
 * last; each with its albums in the given order.
 */
fun albumYears(albums: List<Album>): List<YearAlbums> =
    albums
        .groupBy { it.year }
        .map { (year, list) -> YearAlbums(year, list) }
        .sortedWith(compareBy(nullsLast(reverseOrder())) { it.year })
