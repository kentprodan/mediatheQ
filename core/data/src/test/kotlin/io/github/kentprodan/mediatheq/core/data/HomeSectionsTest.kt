package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HomeSectionsTest {
    private val now = Instant.fromEpochSeconds(1_760_000_000)

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ada"), "Ada")
    private val bo = Artist(id("bo"), "Bo", userData = UserData(isFavorite = true))

    private fun album(
        name: String,
        artist: Artist = ada,
        added: Int? = null,
        favorite: Boolean = false,
    ) = Album(
        id(name),
        name,
        listOf(artist),
        dateAdded = added?.let { now - it.days },
        userData = UserData(isFavorite = favorite),
    )

    private fun track(
        name: String,
        album: Album? = null,
        number: Int = 1,
        plays: Int = 0,
        playedDaysAgo: Int? = null,
        added: Int? = null,
        favorite: Boolean = false,
        rating: Double? = null,
    ) = Track(
        id(name),
        name,
        album?.albumArtists.orEmpty(),
        3.minutes,
        albumId = album?.id,
        trackNumber = number,
        dateAdded = added?.let { now - it.days },
        userData =
            UserData(
                isFavorite = favorite,
                rating = rating,
                playCount = plays,
                lastPlayedAt = playedDaysAgo?.let { now - it.days },
            ),
    )

    private fun items(
        section: HomeSection,
        library: HomeLibrary,
        limit: Int = 16,
        seed: Long = 1,
    ) = homeItems(section, library, limit, seed, now)

    private fun HomeItems.names(): List<String> =
        when (this) {
            is HomeItems.Albums -> albums.map { it.title }
            is HomeItems.Artists -> artists.map { it.name }
            is HomeItems.Tracks -> tracks.map { it.title }
            is HomeItems.Playlists -> playlists.map { it.name }
        }

    @Test
    fun `favorites of every kind, in library order`() {
        val one = album("One", favorite = true)
        val library =
            HomeLibrary(
                albums = listOf(album("Zero"), one),
                artists = listOf(ada, bo),
                tracks = listOf(track("a", favorite = true), track("b")),
                playlists =
                    listOf(
                        Playlist(id("p1"), "Drive", userData = UserData(isFavorite = true)),
                        Playlist(id("p2"), "Gym"),
                    ),
            )

        assertThat(items(HomeSection.FavoriteAlbums, library).names()).containsExactly("One")
        assertThat(items(HomeSection.FavoriteArtists, library).names()).containsExactly("Bo")
        assertThat(items(HomeSection.FavoriteTracks, library).names()).containsExactly("a")
        assertThat(items(HomeSection.FavoritePlaylists, library).names()).containsExactly("Drive")
    }

    @Test
    fun `recently added albums, tracks and artists come newest first, up to the limit`() {
        val old = album("Old", artist = ada, added = 30)
        val new = album("New", artist = bo, added = 1)
        val library =
            HomeLibrary(
                albums = listOf(old, new, album("Undated")),
                artists = listOf(ada, bo),
                tracks = listOf(track("t1", added = 5), track("t2", added = 2), track("t3")),
            )

        assertThat(items(HomeSection.RecentlyAddedAlbums, library).names()).containsExactly("New", "Old").inOrder()
        assertThat(items(HomeSection.RecentlyAddedAlbums, library, limit = 1).names()).containsExactly("New")
        assertThat(items(HomeSection.RecentlyAddedTracks, library).names()).containsExactly("t2", "t1").inOrder()
        assertThat(items(HomeSection.RecentlyAddedArtists, library).names()).containsExactly("Bo", "Ada").inOrder()
    }

    @Test
    fun `an album counts as played when any of its tracks was, last played first`() {
        val a = album("A")
        val b = album("B", artist = bo)
        val library =
            HomeLibrary(
                albums = listOf(a, b, album("Never")),
                artists = listOf(ada, bo),
                tracks = listOf(track("a1", a, playedDaysAgo = 10), track("b1", b, playedDaysAgo = 2)),
            )

        assertThat(items(HomeSection.LastPlayedAlbums, library).names()).containsExactly("B", "A").inOrder()
        assertThat(items(HomeSection.LastPlayedArtists, library).names()).containsExactly("Bo", "Ada").inOrder()
        assertThat(items(HomeSection.LastPlayedTracks, library).names()).containsExactly("b1", "a1").inOrder()
    }

    @Test
    fun `most played albums go by their most played track, artists by all their plays`() {
        val long = album("Long")
        val loved = album("Loved", artist = bo)
        // Ten tracks played once each: one play per track, not ten.
        val longTracks = (1..10).map { track("l$it", long, number = it, plays = 1) }
        val lovedTracks = listOf(track("v1", loved, plays = 4))
        val library =
            HomeLibrary(
                albums = listOf(long, loved),
                artists = listOf(ada, bo),
                tracks = longTracks + lovedTracks,
            )

        assertThat(items(HomeSection.MostPlayedAlbums, library).names()).containsExactly("Loved", "Long").inOrder()
        assertThat(items(HomeSection.MostPlayedArtists, library).names()).containsExactly("Ada", "Bo").inOrder()
        assertThat(items(HomeSection.MostPlayedTracks, library).names().first()).isEqualTo("v1")
    }

    @Test
    fun `rediscover wants at least 3 plays and none in the last 90 days`() {
        val forgotten = album("Forgotten")
        val recent = album("Recent")
        val rare = album("Rare")
        val library =
            HomeLibrary(
                albums = listOf(forgotten, recent, rare),
                tracks =
                    listOf(
                        track("f", forgotten, plays = 5, playedDaysAgo = 120),
                        track("r", recent, plays = 9, playedDaysAgo = 10),
                        track("x", rare, plays = 2, playedDaysAgo = 200),
                    ),
            )

        assertThat(items(HomeSection.RediscoverAlbums, library).names()).containsExactly("Forgotten")
        assertThat(items(HomeSection.RediscoverTracks, library).names()).containsExactly("f")
    }

    @Test
    fun `continue listening lists albums left before their last track, judged by all tracks while offline`() {
        val left = album("Left")
        val done = album("Done")
        val leftTracks = listOf(track("l1", left, 1, playedDaysAgo = 3), track("l2", left, 2))
        val doneTracks = listOf(track("d1", done, 1, playedDaysAgo = 9), track("d2", done, 2, playedDaysAgo = 5))
        val online = HomeLibrary(albums = listOf(left, done), tracks = leftTracks + doneTracks)
        // Offline only "Done" has a download, and only its first track: still finished, by all its tracks.
        val offline =
            HomeLibrary(
                albums = listOf(done),
                tracks = listOf(doneTracks[0]),
                allTracks =
                    leftTracks + doneTracks,
            )

        assertThat(items(HomeSection.ContinueAlbums, online).names()).containsExactly("Left")
        assertThat(items(HomeSection.ContinueAlbums, offline).isEmpty).isTrue()
    }

    @Test
    fun `random sections draw the same set for the same seed and another for another`() {
        val albums = (1..30).map { album("A$it") }
        val library = HomeLibrary(albums = albums)

        val first = items(HomeSection.RandomAlbums, library, seed = 7).names()
        assertThat(items(HomeSection.RandomAlbums, library, seed = 7).names()).isEqualTo(first)
        assertThat(items(HomeSection.RandomAlbums, library, seed = 8).names()).isNotEqualTo(first)
        assertThat(first).hasSize(16)
    }

    @Test
    fun `five stars is a rating that shows as five stars`() {
        val library =
            HomeLibrary(
                tracks =
                    listOf(
                        track("top", rating = 10.0),
                        track("almost", rating = 9.6),
                        track("four", rating = 8.0),
                    ),
            )

        assertThat(items(HomeSection.FiveStarTracks, library).names()).containsExactly("top", "almost")
    }

    @Test
    fun `offline, album sections list only downloaded albums but judge them by all their tracks`() {
        val kept = album("Kept")
        val gone = album("Gone")
        // Offline only the first track of "Kept" is on the phone; its other track holds the plays and the date.
        val keptTracks = listOf(track("k1", kept, 1), track("k2", kept, 2, plays = 6, playedDaysAgo = 120))
        val goneTracks = listOf(track("g1", gone, plays = 9, playedDaysAgo = 150))
        val offline =
            HomeLibrary(albums = listOf(kept), tracks = listOf(keptTracks[0]), allTracks = keptTracks + goneTracks)

        assertThat(items(HomeSection.LastPlayedAlbums, offline).names()).containsExactly("Kept")
        assertThat(items(HomeSection.MostPlayedAlbums, offline).names()).containsExactly("Kept")
        assertThat(items(HomeSection.RediscoverAlbums, offline).names()).containsExactly("Kept")
    }

    @Test
    fun `random artists and tracks follow the seed too`() {
        val library =
            HomeLibrary(
                artists = (1..30).map { Artist(id("ar$it"), "Artist $it") },
                tracks = (1..30).map { track("t$it") },
            )

        listOf(HomeSection.RandomArtists, HomeSection.RandomTracks).forEach { section ->
            val first = items(section, library, seed = 3).names()
            assertThat(items(section, library, seed = 3).names()).isEqualTo(first)
            assertThat(items(section, library, seed = 4).names()).isNotEqualTo(first)
        }
    }
}
