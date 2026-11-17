package io.github.kentprodan.mediatheq.feature.tv.video

import io.github.kentprodan.mediatheq.core.common.nameComparator
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoCollection
import io.github.kentprodan.mediatheq.core.ui.tv.Letter
import io.github.kentprodan.mediatheq.core.ui.tv.alphabetIndex
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Instant

/** One poster of a TV video grid (#426): what the card shows and what the sorts and filters read. */
data class TvPoster(
    val id: MediaId,
    val title: String,
    val sortName: String = title,
    val year: Int? = null,
    val artwork: Artwork? = null,
    val watched: Boolean = false,
    /** How far a started movie got, 0-1; `null` when not started. */
    val progress: Float? = null,
    val favorite: Boolean = false,
    val genres: List<String> = emptyList(),
    val dateAdded: Instant? = null,
    /** A movie's length; `null` for a series. */
    val runtime: Duration? = null,
    /** The public rating, 0-10. */
    val rating: Double? = null,
)

/** The sorts of a TV video grid; [Collection] keeps a collection's own order. */
enum class TvVideoSort { Collection, Name, DateAdded, Year, Rating, Runtime, Random }

enum class TvWatched { All, Unwatched, Watched }

/** The filters of a TV video grid; [decade] is its first year (1990). */
data class TvVideoFilter(
    val watched: TvWatched = TvWatched.All,
    val favorites: Boolean = false,
    val genre: String? = null,
    val decade: Int? = null,
) {
    val active: Int get() =
        listOfNotNull(
            watched.takeIf {
                it != TvWatched.All
            },
            favorites.takeIf { it },
            genre,
            decade,
        ).size
}

/** The grid's posters after [filter] and [sort], with the A-Z strip when sorted by name. */
data class TvArranged(
    val posters: List<TvPoster>,
    val letters: List<Letter>,
)

/**
 * Filters and sorts [posters]. Name ignores case and accents, like the music lists; the highest come first for
 * Date added, Year and Rating, the longest for Runtime, items without the value last. Random shuffles with [seed], so the
 * order stays put until Random is chosen again.
 */
fun arrange(
    posters: List<TvPoster>,
    sort: TvVideoSort,
    filter: TvVideoFilter,
    seed: Long,
): TvArranged {
    val kept =
        posters.filter { p ->
            when (filter.watched) {
                TvWatched.All -> true
                TvWatched.Unwatched -> !p.watched
                TvWatched.Watched -> p.watched
            } &&
                (!filter.favorites || p.favorite) &&
                (filter.genre == null || filter.genre in p.genres) &&
                (filter.decade == null || p.year?.let(::decadeOf) == filter.decade)
        }
    val sorted =
        when (sort) {
            // The music's name order (case and accents ignored), which the A-Z strip expects; SQLite's is bytewise.
            TvVideoSort.Collection -> kept

            TvVideoSort.Name -> kept.sortedWith(compareBy(nameComparator()) { it.sortName })

            TvVideoSort.DateAdded -> kept.sortedWith(newestFirst<Instant> { it.dateAdded })

            TvVideoSort.Year -> kept.sortedWith(newestFirst<Int> { it.year })

            TvVideoSort.Rating -> kept.sortedWith(newestFirst<Double> { it.rating })

            TvVideoSort.Runtime -> kept.sortedWith(newestFirst<Duration> { it.runtime })

            TvVideoSort.Random -> kept.shuffled(Random(seed))
        }
    val letters = if (sort == TvVideoSort.Name) alphabetIndex(sorted.map { it.sortName }) else emptyList()
    return TvArranged(sorted, letters)
}

/** The genres of [posters], most used first, then by name: the Genre filter's choices. */
fun genresOf(posters: List<TvPoster>): List<String> =
    posters
        .flatMap { it.genres }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

/** The decades of [posters], newest first: the Decade filter's choices. */
fun decadesOf(posters: List<TvPoster>): List<Int> =
    posters.mapNotNull { it.year?.let(::decadeOf) }.distinct().sortedDescending()

private fun decadeOf(year: Int) = year / 10 * 10

/** The largest [value] first, posters without one last; a stable sort keeps the name order among equals. */
private fun <T : Comparable<T>> newestFirst(value: (TvPoster) -> T?): Comparator<TvPoster> =
    compareBy(nullsLast(reverseOrder<T>()), value)

internal fun Video.toPoster() =
    TvPoster(
        id = id,
        title = title,
        sortName = sortName,
        year = year,
        artwork = artwork,
        watched = userData.played,
        progress =
            userData.playedPercentage?.let { (it / 100).toFloat() }
                ?: duration.takeIf { it.isPositive() }?.let { (userData.playbackPosition / it).toFloat() }?.takeIf {
                    it >
                        0f
                },
        favorite = userData.isFavorite,
        genres = genres,
        dateAdded = dateAdded,
        runtime = duration.takeIf { it.isPositive() },
        rating = communityRating,
    )

internal fun VideoCollection.toPoster() =
    TvPoster(id = id, title = title, sortName = sortName, year = year, artwork = artwork, dateAdded = dateAdded)

internal fun Series.toPoster() =
    TvPoster(
        id = id,
        title = title,
        sortName = sortName,
        year = year,
        artwork = artwork,
        watched = userData.played,
        favorite = userData.isFavorite,
        genres = genres,
        dateAdded = dateAdded,
        rating = communityRating,
    )
