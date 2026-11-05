package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MixesTest {
    private val now = Instant.fromEpochSeconds(1_760_000_000)

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun album(
        name: String,
        year: Int? = null,
    ) = Album(id(name), name, emptyList(), year = year)

    private fun track(
        name: String,
        album: Album? = null,
        disc: Int? = null,
        number: Int? = null,
        favorite: Boolean = false,
        rating: Double? = null,
        playedDaysAgo: Int? = null,
    ) = Track(
        id(name),
        name,
        emptyList(),
        3.minutes,
        albumId = album?.id,
        trackNumber = number,
        discNumber = disc,
        userData =
            UserData(isFavorite = favorite, rating = rating, lastPlayedAt = playedDaysAgo?.let { now - it.days }),
    )

    private fun mix(
        mix: Mix,
        tracks: List<Track>,
        albums: List<Album> = emptyList(),
        seed: Int = 1,
    ) = mixTracks(mix, tracks, albums, Random(seed), now).map { it.title }

    @Test
    fun `the same seed gives the same track mix`() {
        val tracks = (1..20).map { track("t$it") }

        assertThat(mix(Mix.Tracks, tracks, seed = 7)).isEqualTo(mix(Mix.Tracks, tracks, seed = 7))
        assertThat(mix(Mix.Tracks, tracks, seed = 7)).containsExactlyElementsIn(tracks.map { it.title })
    }

    @Test
    fun `a favorite comes before a plain track about three times in four`() {
        val tracks = listOf(track("plain"), track("favorite", favorite = true))

        val favoriteFirst = (0 until 1000).count { mix(Mix.Tracks, tracks, seed = it).first() == "favorite" }

        assertThat(favoriteFirst).isIn(700..800)
    }

    @Test
    fun `weights grow with favorite and rating`() {
        assertThat(track("plain").mixWeight).isEqualTo(1.0)
        assertThat(track("five stars", rating = 10.0).mixWeight).isEqualTo(6.0)
        assertThat(track("both", favorite = true, rating = 10.0).mixWeight).isEqualTo(8.0)
    }

    @Test
    fun `tracks played in the last 14 days go to the end`() {
        val tracks =
            listOf(
                track("yesterday", favorite = true, rating = 10.0, playedDaysAgo = 1),
                track("13 days", playedDaysAgo = 13),
                track("15 days", playedDaysAgo = 15),
                track("never"),
            )

        repeat(20) { seed ->
            assertThat(mix(Mix.Tracks, tracks, seed = seed).takeLast(2)).containsExactly("yesterday", "13 days")
        }
    }

    @Test
    fun `album mix plays whole albums in disc and track order`() {
        val a = album("a")
        val b = album("b")
        val tracks =
            listOf(
                track("b2", b, number = 2),
                track("a-d2-1", a, disc = 2, number = 1),
                track("a-d1-9", a, disc = 1, number = 9),
                track("b1", b, number = 1),
                track("a-d1-1", a, disc = 1, number = 1),
                track("single"),
            )

        val orders = (0 until 20).map { mix(Mix.Albums, tracks, seed = it) }.toSet()

        assertThat(orders)
            .containsExactly(
                listOf("a-d1-1", "a-d1-9", "a-d2-1", "b1", "b2"),
                listOf("b1", "b2", "a-d1-1", "a-d1-9", "a-d2-1"),
            )
    }

    @Test
    fun `decade mix keeps tracks of albums from that decade`() {
        val old = album("old", 1989)
        val start = album("start", 1990)
        val end = album("end", 1999)
        val next = album("next", 2000)
        val tracks = listOf(old, start, end, next).map { track(it.title, it) } + track("unknown", album("none"))

        assertThat(mix(Mix.Decade(1990), tracks, listOf(old, start, end, next)))
            .containsExactly("start", "end")
    }

    @Test
    fun `decades are those of the library, oldest first`() {
        val albums = listOf(album("a", 2003), album("b", 1971), album("c", 1979), album("d"), album("e", 2000))

        assertThat(decades(albums)).containsExactly(1970, 2000).inOrder()
    }
}
