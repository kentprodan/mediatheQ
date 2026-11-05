package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A mix (#128): the whole matching library queued in one go, so it plays on without choosing an album. */
sealed interface Mix {
    /** Every track, favorites and high ratings first more often, recently played ones last. */
    data object Tracks : Mix

    /** Every album, in random order, each one whole in track order. */
    data object Albums : Mix

    /** A track mix of the albums released in the ten years from [startYear] (1990 = the 1990s). */
    data class Decade(
        val startYear: Int,
    ) : Mix
}

/** A track played this recently goes to the end of a track mix (owner, 2026-11-05). */
val MIX_RECENTLY_PLAYED = 14.days

/**
 * The tracks of [mix], in play order. [tracks] and [albums] are the library lists (only downloads while offline, ADR
 * 0021); [random] draws the order and [now] dates "recently played".
 */
fun mixTracks(
    mix: Mix,
    tracks: List<Track>,
    albums: List<Album>,
    random: Random,
    now: Instant,
): List<Track> =
    when (mix) {
        Mix.Tracks -> {
            trackMix(tracks, random, now)
        }

        Mix.Albums -> {
            tracks
                .filter { it.albumId != null }
                .groupBy { it.albumId }
                .values
                .shuffled(random)
                .flatMap { album -> album.sortedBy { (it.discNumber ?: 1) * DISC_STEP + (it.trackNumber ?: 0) } }
        }

        is Mix.Decade -> {
            val years = mix.startYear until mix.startYear + DECADE
            val inDecade = albums.filter { it.year in years }.map { it.id }.toSet()
            trackMix(tracks.filter { it.albumId in inDecade }, random, now)
        }
    }

/** The decades of [albums]' years, oldest first: the Decade mix offers only these. */
fun decades(albums: List<Album>): List<Int> =
    albums
        .mapNotNull { it.year }
        .map { it / DECADE * DECADE }
        .distinct()
        .sorted()

private fun trackMix(
    tracks: List<Track>,
    random: Random,
    now: Instant,
): List<Track> {
    val cutoff = now - MIX_RECENTLY_PLAYED
    val (recent, rest) = tracks.partition { track -> track.userData.lastPlayedAt?.let { it > cutoff } == true }
    return weightedOrder(rest, random) + weightedOrder(recent, random)
}

/** A random order where heavier tracks come early more often (Efraimidis-Spirakis: key = u^(1/weight)). */
private fun weightedOrder(
    tracks: List<Track>,
    random: Random,
): List<Track> =
    tracks
        .map {
            it to random.nextDouble().pow(1.0 / it.mixWeight)
        }.sortedByDescending { it.second }
        .map { it.first }

/** 1, plus 2 for a favorite, plus half the server's 0-10 rating: a five-star favorite weighs 8. */
internal val Track.mixWeight: Double
    get() = 1.0 + (if (userData.isFavorite) 2.0 else 0.0) + (userData.rating ?: 0.0) / 2

private const val DECADE = 10
private const val DISC_STEP = 10_000
