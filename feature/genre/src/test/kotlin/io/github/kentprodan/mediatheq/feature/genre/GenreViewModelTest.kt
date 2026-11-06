package io.github.kentprodan.mediatheq.feature.genre

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class GenreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val trackTaps by lazy { TrackTaps(playback, preferences) }
    private val commands by lazy {
        TrackCommands(playback, FakeUserDataRepository(), FakeDownloadRepository(), preferences)
    }

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val genreId = id("g-rock")
    private val genre = Genre(genreId, "Rock", albumCount = 1, trackCount = 12)
    private val artist = Artist(id("ar1"), "Subcarpați")
    private val guest = Artist(id("ar9"), "Guest")
    private val album = Album(id("al1"), "Satelit", listOf(artist))
    private val tracks = List(12) { Track(id("t$it"), "Track $it", listOf(artist), 3.minutes) }

    private fun withGenre() {
        library.genres.value = listOf(genre)
        library.genreAlbums.value = mapOf(genreId to listOf(album))
        library.genreArtists.value = mapOf(genreId to listOf(artist))
        library.genreTracks.value = mapOf(genreId to tracks)
        library.genreTrackArtists.value = mapOf(genreId to listOf(artist, guest))
    }

    private fun newViewModel() = GenreViewModel(genreId, library, preferences, playback, trackTaps, commands)

    private fun TestScope.viewModel(): GenreViewModel {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun GenreViewModel.content() = uiState.value as GenreUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `before the first read the page is loading`() =
        test {
            withGenre()

            assertThat(newViewModel().uiState.value).isEqualTo(GenreUiState.Loading)
        }

    @Test
    fun `the page shows albums, album artists, credited artists and the top tracks, as many as the setting says`() =
        test {
            withGenre()
            val viewModel = viewModel()

            assertThat(viewModel.content().albums).containsExactly(album)
            assertThat(viewModel.content().albumArtists).containsExactly(artist)
            assertThat(viewModel.content().trackArtists).containsExactly(artist, guest).inOrder()
            assertThat(viewModel.content().topTracks).hasSize(12)

            preferences.setGenrePage(GenrePagePreferences(topTracksCount = 5))
            assertThat(viewModel.content().topTracks).hasSize(5)
            assertThat(viewModel.content().rankedTracks).hasSize(12)
        }

    @Test
    fun `top tracks are most played first, then unplayed by name, so a genre nobody played still lists its tracks`() {
        val played = tracks.mapIndexed { i, track -> track.copy(userData = UserData(playCount = if (i == 4) 9 else 0)) }

        assertThat(rankTracks(played).first()).isEqualTo(played[4])
        assertThat(rankTracks(played).drop(1)).isEqualTo(played - played[4])
        assertThat(rankTracks(tracks)).isEqualTo(tracks)
    }

    @Test
    fun `sections follow the chosen order and switches, and empty ones are left out`() =
        test {
            withGenre()
            val viewModel = viewModel()

            assertThat(viewModel.content().sections)
                .containsExactly(GenreSection.Artists, GenreSection.Albums, GenreSection.TopTracks)
                .inOrder()

            preferences.setGenrePage(
                GenrePagePreferences(
                    sectionOrder =
                        listOf(
                            GenreSection.TopTracks,
                            GenreSection.AlbumArtists,
                            GenreSection.Albums,
                            GenreSection.Artists,
                        ),
                    showAlbumArtists = true,
                    showArtists = false,
                ),
            )
            assertThat(viewModel.content().sections)
                .containsExactly(GenreSection.TopTracks, GenreSection.AlbumArtists, GenreSection.Albums)
                .inOrder()

            // Offline the repository gives only what is downloaded: here no album of the genre.
            library.genreAlbums.value = mapOf(genreId to emptyList())
            library.genreArtists.value = mapOf(genreId to emptyList())
            assertThat(viewModel.content().sections).containsExactly(GenreSection.TopTracks)
        }

    @Test
    fun `the header shows the most played album's cover, else the first album's, else none`() {
        val cover1 = Artwork("al1", "c1")
        val cover2 = Artwork("al2", "c2")
        val inFirst = Track(id("x1"), "x1", emptyList(), 3.minutes, albumId = id("al1"), artwork = cover1)
        val inSecond =
            Track(
                id("x2"),
                "x2",
                emptyList(),
                3.minutes,
                albumId = id("al2"),
                artwork = cover2,
                userData = UserData(playCount = 4),
            )
        val first = album.copy(artwork = cover1)

        assertThat(headerArtwork(listOf(inFirst, inSecond), listOf(first), genre)).isEqualTo(cover2)
        assertThat(headerArtwork(listOf(inFirst), listOf(first), genre)).isEqualTo(cover1)
        assertThat(headerArtwork(emptyList(), emptyList(), genre)).isNull()
        assertThat(headerArtwork(emptyList(), listOf(album), genre.copy(artwork = cover2))).isEqualTo(cover2)
    }

    @Test
    fun `the header buttons queue the genre, and a top track plays the shown list or every track from it`() =
        test {
            withGenre()
            val viewModel = viewModel()
            preferences.setGenrePage(GenrePagePreferences(topTracksCount = 5))

            viewModel.onAction(GenreAction.PlayNext)
            viewModel.onAction(GenreAction.AddToQueue)
            viewModel.onAction(GenreAction.PlayTrack(tracks[2]))
            viewModel.onAction(GenreAction.PlayTrack(tracks[8], all = true))

            assertThat(playback.playedNext).containsExactly(tracks)
            assertThat(playback.addedToQueue).containsExactly(tracks)
            assertThat(playback.plays)
                .containsExactly(PlayCall(tracks.take(5), 2, false), PlayCall(tracks, 8, false))
                .inOrder()
            assertThat(viewModel.content().duration).isEqualTo(36.minutes)
        }

    @Test
    fun `a genre nothing carries, or nothing offline, is not found`() =
        test {
            assertThat(viewModel().uiState.value).isEqualTo(GenreUiState.NotFound)
        }

    @Test
    fun `play and shuffle send every track of the genre`() =
        test {
            withGenre()
            val viewModel = viewModel()

            viewModel.onAction(GenreAction.Play)
            viewModel.onAction(GenreAction.Shuffle)

            assertThat(playback.plays).containsExactly(PlayCall(tracks, 0, false), PlayCall(tracks, 0, true)).inOrder()
        }
}
