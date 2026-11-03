package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What a Home section shows: items of one kind. */
sealed interface HomeItems {
    val isEmpty: Boolean

    data class Albums(
        val albums: List<Album>,
    ) : HomeItems {
        override val isEmpty: Boolean get() = albums.isEmpty()
    }

    data class Artists(
        val artists: List<Artist>,
    ) : HomeItems {
        override val isEmpty: Boolean get() = artists.isEmpty()
    }

    data class Tracks(
        val tracks: List<Track>,
    ) : HomeItems {
        override val isEmpty: Boolean get() = tracks.isEmpty()
    }

    data class Playlists(
        val playlists: List<Playlist>,
    ) : HomeItems {
        override val isEmpty: Boolean get() = playlists.isEmpty()
    }
}

/**
 * The library as Home reads it (#127): [albums], [artists], [tracks] and [playlists] as the Library lists them (only
 * downloads while offline, ADR 0021); [allTracks] always the whole library, so an album or artist is judged by all its
 * tracks (a half-downloaded album is not "unfinished") while offline only what has a download is listed.
 */
data class HomeLibrary(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val allTracks: List<Track> = tracks,
    val playlists: List<Playlist> = emptyList(),
) {
    /** Per album, from all its tracks. */
    internal val albumStats: Map<MediaId, AlbumStats> by lazy {
        allTracks
            .filter { it.albumId != null }
            .groupBy { it.albumId!! }
            .mapValues { (_, tracks) -> AlbumStats(tracks) }
    }
}

/** An album's play history, from all its tracks. */
internal class AlbumStats(
    tracks: List<Track>,
) {
    val lastPlayed: Instant? = tracks.mapNotNull { it.userData.lastPlayedAt }.maxOrNull()

    /** The most played track's count: a 10-track album played once counts 1, not 10. */
    val mostPlays: Int = tracks.maxOf { it.userData.playCount }

    val totalPlays: Int = tracks.sumOf { it.userData.playCount }

    /** The album was left before its end: the last track played is not its last track. */
    val unfinished: Boolean =
        tracks.maxByOrNull { it.userData.lastPlayedAt ?: Instant.DISTANT_PAST }?.let { last ->
            last.userData.lastPlayedAt != null && last.position < tracks.maxOf { it.position }
        } == true

    private val Track.position: Int get() = (discNumber ?: 1) * DISC_STEP + (trackNumber ?: 0)

    private companion object {
        const val DISC_STEP = 10_000
    }
}

/** "Played often": the most played track (or the track itself) has at least this many plays. */
const val REDISCOVER_MIN_PLAYS = 3

/** ... and was last played longer ago than this (owner, 2026-11-03). */
val REDISCOVER_AFTER = 90.days

/**
 * The items of [section] (#127), at most [limit]. [seed] draws the random sections (the same seed, the same set);
 * [now] dates the rediscover sections.
 */
fun homeItems(
    section: HomeSection,
    library: HomeLibrary,
    limit: Int,
    seed: Long,
    now: Instant,
): HomeItems {
    val stats = library.albumStats
    val cutoff = now - REDISCOVER_AFTER
    val albumLastPlayed = { album: Album ->
        listOfNotNull(album.userData.lastPlayedAt, stats[album.id]?.lastPlayed).maxOrNull()
    }
    // An artist's albums, from the listed albums.
    val albumsOf by lazy {
        library.albums
            .flatMap { album -> album.albumArtists.map { it.id to album } }
            .groupBy({ it.first }, { it.second })
    }
    val albums: (List<Album>) -> HomeItems = { HomeItems.Albums(it.take(limit)) }
    val artists: (List<Artist>) -> HomeItems = { HomeItems.Artists(it.take(limit)) }
    val tracks: (List<Track>) -> HomeItems = { HomeItems.Tracks(it.take(limit)) }
    return when (section) {
        HomeSection.FavoritePlaylists -> {
            HomeItems.Playlists(library.playlists.filter { it.userData.isFavorite }.take(limit))
        }

        HomeSection.FavoriteAlbums -> {
            albums(library.albums.filter { it.userData.isFavorite })
        }

        HomeSection.FavoriteTracks -> {
            tracks(library.tracks.filter { it.userData.isFavorite })
        }

        HomeSection.FavoriteArtists -> {
            artists(library.artists.filter { it.userData.isFavorite })
        }

        HomeSection.RecentlyAddedAlbums -> {
            albums(library.albums.filter { it.dateAdded != null }.sortedByDescending { it.dateAdded })
        }

        HomeSection.RediscoverAlbums -> {
            albums(
                library.albums
                    .filter { album ->
                        val played = albumLastPlayed(album)
                        (stats[album.id]?.mostPlays ?: 0) >= REDISCOVER_MIN_PLAYS && played != null && played < cutoff
                    }.sortedByDescending { stats[it.id]?.mostPlays },
            )
        }

        HomeSection.LastPlayedAlbums -> {
            albums(
                library.albums
                    .mapNotNull { album -> albumLastPlayed(album)?.let { album to it } }
                    .sortedByDescending { it.second }
                    .map { it.first },
            )
        }

        HomeSection.ContinueAlbums -> {
            albums(
                library.albums
                    .filter { stats[it.id]?.unfinished == true }
                    .sortedByDescending { albumLastPlayed(it) },
            )
        }

        HomeSection.MostPlayedAlbums -> {
            albums(
                library.albums
                    .filter { (stats[it.id]?.mostPlays ?: 0) > 0 }
                    .sortedByDescending { stats[it.id]?.mostPlays },
            )
        }

        HomeSection.RandomAlbums -> {
            albums(library.albums.shuffled(Random(seed)))
        }

        HomeSection.RecentlyAddedArtists -> {
            artists(
                library.artists
                    .mapNotNull { artist ->
                        albumsOf[artist.id]?.mapNotNull { it.dateAdded }?.maxOrNull()?.let {
                            artist to
                                it
                        }
                    }.sortedByDescending { it.second }
                    .map { it.first },
            )
        }

        HomeSection.LastPlayedArtists -> {
            artists(
                library.artists
                    .mapNotNull { artist ->
                        albumsOf[artist.id]?.mapNotNull(albumLastPlayed)?.maxOrNull()?.let { artist to it }
                    }.sortedByDescending { it.second }
                    .map { it.first },
            )
        }

        HomeSection.MostPlayedArtists -> {
            // All plays of all their albums' tracks: an artist is played by what is heard of them.
            artists(
                library.artists
                    .map { artist -> artist to albumsOf[artist.id].orEmpty().sumOf { stats[it.id]?.totalPlays ?: 0 } }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .map { it.first },
            )
        }

        HomeSection.RandomArtists -> {
            artists(library.artists.shuffled(Random(seed)))
        }

        HomeSection.MostPlayedTracks -> {
            tracks(library.tracks.filter { it.userData.playCount > 0 }.sortedByDescending { it.userData.playCount })
        }

        HomeSection.RecentlyAddedTracks -> {
            tracks(library.tracks.filter { it.dateAdded != null }.sortedByDescending { it.dateAdded })
        }

        HomeSection.LastPlayedTracks -> {
            tracks(
                library.tracks
                    .filter {
                        it.userData.lastPlayedAt != null
                    }.sortedByDescending { it.userData.lastPlayedAt },
            )
        }

        HomeSection.RandomTracks -> {
            tracks(library.tracks.shuffled(Random(seed)))
        }

        HomeSection.RediscoverTracks -> {
            tracks(
                library.tracks
                    .filter { track ->
                        val played = track.userData.lastPlayedAt
                        track.userData.playCount >= REDISCOVER_MIN_PLAYS && played != null && played < cutoff
                    }.sortedByDescending { it.userData.playCount },
            )
        }

        HomeSection.FiveStarTracks -> {
            // The server's 0-10 rating, shown as half stars: 10 half stars are 5 stars.
            tracks(library.tracks.filter { it.userData.halfStars == FIVE_STARS })
        }
    }
}

private const val FIVE_STARS = 10
