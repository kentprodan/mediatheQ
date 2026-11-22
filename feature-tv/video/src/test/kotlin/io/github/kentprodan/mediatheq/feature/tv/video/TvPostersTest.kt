package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.ui.tv.Letter
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TvPostersTest {
    private fun poster(
        title: String,
        year: Int? = null,
        watched: Boolean = false,
        favorite: Boolean = false,
        genres: List<String> = emptyList(),
        added: Long? = null,
        minutes: Int? = null,
        rating: Double? = null,
    ) = TvPoster(
        MediaId(ServerId("s"), title),
        title,
        year = year,
        watched = watched,
        favorite = favorite,
        genres = genres,
        dateAdded = added?.let(Instant::fromEpochSeconds),
        runtime = minutes?.minutes,
        rating = rating,
    )

    private val alien =
        poster(
            "Alien",
            1979,
            watched = true,
            genres = listOf("Horror", "Sci-Fi"),
            added = 30,
            minutes = 117,
            rating = 8.5,
        )
    private val brazil = poster("Brazil", 1985, favorite = true, genres = listOf("Sci-Fi"), added = 10, minutes = 142)
    private val heat = poster("Heat", 1995, genres = listOf("Crime"), added = 20, rating = 8.3)
    private val unknown = poster("Zelig")
    private val all = listOf(alien, brazil, heat, unknown)

    private fun titles(
        sort: TvVideoSort,
        filter: TvVideoFilter = TvVideoFilter(),
        seed: Long = 0,
    ) = arrange(all, sort, filter, seed).posters.map { it.title }

    @Test
    fun `name keeps the database order and gives the letters`() {
        val arranged = arrange(all, TvVideoSort.Name, TvVideoFilter(), 0)

        assertThat(arranged.posters).isEqualTo(all)
        assertThat(arranged.letters)
            .containsExactly(Letter('A', 0), Letter('B', 1), Letter('H', 2), Letter('Z', 3))
            .inOrder()
        assertThat(arrange(all, TvVideoSort.Year, TvVideoFilter(), 0).letters).isEmpty()
    }

    @Test
    fun `name ignores case and accents`() {
        val names = listOf(poster("zodiac"), poster("Élan"), poster("alien"), poster("Brazil"))

        assertThat(arrange(names, TvVideoSort.Name, TvVideoFilter(), 0).posters.map { it.title })
            .containsExactly("alien", "Brazil", "Élan", "zodiac")
            .inOrder()
    }

    @Test
    fun `date added, year and runtime put the largest first and items without one last`() {
        assertThat(titles(TvVideoSort.DateAdded)).containsExactly("Alien", "Heat", "Brazil", "Zelig").inOrder()
        assertThat(titles(TvVideoSort.Year)).containsExactly("Heat", "Brazil", "Alien", "Zelig").inOrder()
        assertThat(titles(TvVideoSort.Runtime)).containsExactly("Brazil", "Alien", "Heat", "Zelig").inOrder()
        assertThat(titles(TvVideoSort.Rating)).containsExactly("Alien", "Heat", "Brazil", "Zelig").inOrder()
    }

    @Test
    fun `collection order keeps the given order`() {
        assertThat(
            arrange(all.reversed(), TvVideoSort.Collection, TvVideoFilter(), 0).posters,
        ).isEqualTo(all.reversed())
    }

    @Test
    fun `random keeps its order for the same seed`() {
        assertThat(titles(TvVideoSort.Random, seed = 7)).isEqualTo(titles(TvVideoSort.Random, seed = 7))
        assertThat(titles(TvVideoSort.Random, seed = 7)).containsExactlyElementsIn(all.map { it.title })
    }

    @Test
    fun `filters combine`() {
        assertThat(titles(TvVideoSort.Name, TvVideoFilter(watched = TvWatched.Watched))).containsExactly("Alien")
        assertThat(titles(TvVideoSort.Name, TvVideoFilter(watched = TvWatched.Unwatched)))
            .containsExactly("Brazil", "Heat", "Zelig")
            .inOrder()
        assertThat(titles(TvVideoSort.Name, TvVideoFilter(favorites = true))).containsExactly("Brazil")
        assertThat(
            titles(TvVideoSort.Name, TvVideoFilter(genre = "Sci-Fi")),
        ).containsExactly("Alien", "Brazil").inOrder()
        assertThat(titles(TvVideoSort.Name, TvVideoFilter(decade = 1980))).containsExactly("Brazil")
        assertThat(titles(TvVideoSort.Name, TvVideoFilter(watched = TvWatched.Unwatched, genre = "Sci-Fi")))
            .containsExactly("Brazil")
        assertThat(TvVideoFilter(watched = TvWatched.Unwatched, genre = "Sci-Fi").active).isEqualTo(2)
    }

    @Test
    fun `the filter choices are the genres by use and the decades newest first`() {
        assertThat(genresOf(all)).containsExactly("Sci-Fi", "Crime", "Horror").inOrder()
        assertThat(decadesOf(all)).containsExactly(1990, 1980, 1970).inOrder()
    }

    @Test
    fun `a movie's progress comes from its percentage, else its position, and none when not started`() {
        val movie = Video(MediaId(ServerId("s"), "m"), VideoKind.Movie, "M", duration = 100.minutes)

        assertThat(movie.copy(userData = UserData(playedPercentage = 25.0)).toPoster().progress).isEqualTo(0.25f)
        assertThat(movie.copy(userData = UserData(playbackPosition = 50.minutes)).toPoster().progress).isEqualTo(0.5f)
        assertThat(movie.toPoster().progress).isNull()
    }

    @Test
    fun `a genre card shows the picture of its newest poster that has one, and a movie keeps its studios (#522)`() {
        val old = alien.copy(artwork = Artwork("a", "t1"))
        val newer = brazil.copy(dateAdded = Instant.fromEpochSeconds(50), artwork = Artwork("b", "t2"))
        val newestNoPicture = heat.copy(genres = listOf("Sci-Fi"), dateAdded = Instant.fromEpochSeconds(90))

        val sciFi = groupsOf(listOf(old, newer, newestNoPicture)) { it.genres }.single { it.name == "Sci-Fi" }

        assertThat(sciFi.count).isEqualTo(3)
        assertThat(sciFi.artwork).isEqualTo(Artwork("b", "t2"))
        val movie = Video(MediaId(ServerId("s"), "m"), VideoKind.Movie, "M", studios = listOf("A24"))
        assertThat(movie.toPoster().studios).containsExactly("A24")
    }
}
