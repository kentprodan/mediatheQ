package io.github.kentprodan.mediatheq.feature.search

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.SearchSort
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Test
import kotlin.time.Duration

class SearchFiltersTest {
    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun track(name: String) = Track(id("t-$name"), name, emptyList(), Duration.ZERO)

    // In the search's order (best match first), which is not the name order.
    private val tracks = listOf(track("moșoi b"), track("Mosoi a"), track("mosoi c"))
    private val results =
        SearchResults(
            artists = List(25) { Artist(id("ar$it"), "Artist $it") },
            albums = listOf(Album(id("al"), "Album", emptyList())),
            tracks = tracks,
            labels = listOf(RecordLabel(id("l"), "Label")),
        )

    @Test
    fun `All keeps the groups in the search's order, at most 20 of each`() {
        val shown = results.shown(SearchFilter.All, SearchSort.Title)

        assertThat(shown.artists).isEqualTo(results.artists.take(20))
        assertThat(shown.tracks).isEqualTo(tracks)
        assertThat(shown.labels).isEqualTo(results.labels)
    }

    @Test
    fun `a chip shows only its kind, all of it`() {
        val shown = results.shown(SearchFilter.Artists, SearchSort.Relevance)

        assertThat(shown).isEqualTo(SearchResults(artists = results.artists))
    }

    @Test
    fun `Relevance keeps the search's rank order, Title sorts names ignoring case and diacritics`() {
        assertThat(results.shown(SearchFilter.Tracks, SearchSort.Relevance).tracks).isEqualTo(tracks)
        assertThat(results.shown(SearchFilter.Tracks, SearchSort.Title).tracks.map { it.title })
            .containsExactly("Mosoi a", "moșoi b", "mosoi c")
            .inOrder()
    }

    @Test
    fun `same names keep one order by id`() {
        val twins = SearchResults(genres = listOf(Genre(id("g2"), "Jazz"), Genre(id("g1"), "jazz")))

        assertThat(twins.shown(SearchFilter.Genres, SearchSort.Title).genres.map { it.id.itemId })
            .containsExactly("g1", "g2")
            .inOrder()
    }

    @Test
    fun `chips with nothing are empty, All has the record labels`() {
        val onlyLabels = SearchResults(labels = results.labels)

        assertThat(SearchFilter.entries.filter { onlyLabels.has(it) }).containsExactly(SearchFilter.All)
        assertThat(SearchFilter.entries.filter { results.has(it) })
            .containsExactly(SearchFilter.All, SearchFilter.Artists, SearchFilter.Albums, SearchFilter.Tracks)
    }
}
