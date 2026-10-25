package io.github.kentprodan.mediatheq.feature.artist

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
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

class ArtistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val userData = FakeUserDataRepository()
    private val artistId = MediaId(ServerId("s"), "ar1")
    private val artist = Artist(artistId, "Subcarpați")
    private val own = Album(MediaId(ServerId("s"), "al1"), "Satelit", listOf(artist), year = 2016)
    private val compilation = Album(MediaId(ServerId("s"), "al2"), "Hits", emptyList(), year = 2018)

    private fun track(
        id: String,
        plays: Int = 0,
    ) = Track(MediaId(ServerId("s"), id), id, listOf(artist), 3.minutes, userData = UserData(playCount = plays))

    // Own album tracks first, then the credited compilation track (the DAO's order).
    private val tracks = listOf(track("t1", plays = 2), track("t2", plays = 9), track("t3"), track("c1", plays = 2))

    private fun withArtist() {
        library.albumArtists.value = listOf(artist)
        library.artistAlbums.value = mapOf(artistId to listOf(own))
        library.appearsOn.value = mapOf(artistId to listOf(compilation))
        library.artistTracks.value = mapOf(artistId to tracks)
    }

    private fun TestScope.viewModel(): ArtistViewModel {
        val viewModel = ArtistViewModel(artistId, library, preferences, playback, userData)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private val ArtistViewModel.content get() = uiState.value as ArtistUiState.Content

    @Test
    fun `starts loading`() {
        assertThat(ArtistViewModel(artistId, library, preferences, playback, userData).uiState.value)
            .isEqualTo(ArtistUiState.Loading)
    }

    @Test
    fun `an artist missing from the library is not found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel().uiState.value).isEqualTo(ArtistUiState.NotFound)
        }

    @Test
    fun `an artist shows own albums, appears-on albums and every track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()

            val state = viewModel().content

            assertThat(state.artist).isEqualTo(artist)
            assertThat(state.albums).containsExactly(own)
            assertThat(state.appearsOn).containsExactly(compilation)
            assertThat(state.tracks).isEqualTo(tracks)
            assertThat(state.isEmpty).isFalse()
        }

    @Test
    fun `a credit-only artist is found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.creditedArtists.value = listOf(artist)

            val state = viewModel().content

            assertThat(state.isEmpty).isTrue()
        }

    @Test
    fun `top tracks are the most played, ties in discography order, unplayed left out`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()

            assertThat(viewModel().content.topTracks.map { it.title }).containsExactly("t2", "t1", "c1").inOrder()
        }

    @Test
    fun `top tracks stop at five and are empty when nothing was played`() {
        assertThat(topTracks((1..7).map { track("t$it", plays = it) }).map { it.title })
            .containsExactly("t7", "t6", "t5", "t4", "t3")
            .inOrder()
        assertThat(topTracks(listOf(track("t1"), track("t2")))).isEmpty()
    }

    @Test
    fun `albums use the Library's column setting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()
            val viewModel = viewModel()

            preferences.setAlbumColumns(4)

            assertThat(viewModel.content.albumColumns).isEqualTo(4)
        }

    @Test
    fun `the playing track is marked`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()
            val viewModel = viewModel()

            playback.state.value = PlayerUiModel(item = NowPlayingItem(tracks[1].id, "t2", "", null))

            assertThat(viewModel.content.currentTrackId).isEqualTo(tracks[1].id)
        }

    @Test
    fun `play queues every track in order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()

            viewModel().onAction(ArtistAction.Play)

            assertThat(playback.plays).containsExactly(PlayCall(tracks, 0, shuffle = false))
        }

    @Test
    fun `shuffle queues every track of the artist, credits included`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()

            viewModel().onAction(ArtistAction.Shuffle)

            assertThat(playback.plays).containsExactly(PlayCall(tracks, 0, shuffle = true))
        }

    @Test
    fun `a top track plays the top tracks from it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            withArtist()
            val viewModel = viewModel()

            viewModel.onAction(ArtistAction.PlayTopTrack(tracks[0].id))

            assertThat(playback.plays).containsExactly(PlayCall(viewModel.content.topTracks, 1, shuffle = false))
        }

    @Test
    fun `play and shuffle do nothing without tracks`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albumArtists.value = listOf(artist)
            val viewModel = viewModel()

            viewModel.onAction(ArtistAction.Play)
            viewModel.onAction(ArtistAction.Shuffle)

            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `favorite and rating go to the artist`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albumArtists.value = listOf(artist)
            val viewModel = viewModel()

            viewModel.onAction(ArtistAction.SetFavorite(true))
            viewModel.onAction(ArtistAction.Rate(4))

            assertThat(userData.calls).containsExactly("favorite:ar1:true", "rating:ar1:4").inOrder()
        }
}
