package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video

/** A genre row of Home (#431 TV, #441 phone): the library's movies and series of [genre], the newest added first. */
data class GenreRow(
    val genre: String,
    val items: List<GenreItem>,
)

sealed interface GenreItem {
    val id: MediaId

    data class Movie(
        val video: Video,
    ) : GenreItem {
        override val id: MediaId get() = video.id
    }

    data class Show(
        val series: Series,
    ) : GenreItem {
        override val id: MediaId get() = series.id
    }
}

/** One row per picked genre, in the order picked: its movies and series, newest added first; empty ones left out. */
fun genreRows(
    genres: List<String>,
    movies: List<Video>,
    series: List<Series>,
    limit: Int,
): List<GenreRow> =
    genres.mapNotNull { genre ->
        val items =
            (
                movies.filter { genre in it.genres }.map { GenreItem.Movie(it) to it.dateAdded } +
                    series.filter { genre in it.genres }.map { GenreItem.Show(it) to it.dateAdded }
            ).sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        items.takeIf { it.isNotEmpty() }?.let { GenreRow(genre, it) }
    }

/** Every genre of [genreLists] with how many items have it, the most common first, then by name. */
fun genreCounts(genreLists: List<List<String>>): List<Pair<String, Int>> =
    genreLists
        .flatten()
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }
