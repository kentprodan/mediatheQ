package io.github.kentprodan.mediatheq.feature.tv.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.testing.TestClock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TvHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val sync = FakeLibrarySyncRepository()
    private val playback = FakePlaybackConnection()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playlists = FakePlaylistRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun day(n: Int) = Instant.fromEpochSeconds(0) + n.days

    private val ada = Artist(id("ar"), "Ada")
    private val alpha = Album(id("alpha"), "alpha", listOf(ada), dateAdded = day(1))
    private val beta =
        Album(id("beta"), "beta", listOf(ada), dateAdded = day(5), userData = UserData(isFavorite = true))

    private fun track(
        name: String,
        album: Album,
    ) = Track(id(name), name, listOf(ada), Duration.ZERO, albumId = album.id)

    private val tracks = listOf(track("a1", alpha), track("a2", alpha), track("b1", beta))

    private fun newViewModel() =
        TvHomeViewModel(
            library,
            sync,
            playback,
            TrackTaps(playback, preferences),
            playlists,
            preferences,
            HomeShuffle(1),
            TestClock(day(20)),
            mainDispatcherRule.testDispatcher,
        )

    private fun TestScope.viewModel(status: SyncStatus = SyncStatus.Idle(day(10))): TvHomeViewModel {
        sync.status.value = status
        val viewModel =
            newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TestScope.withLibrary(): TvHomeViewModel {
        library.albums.value = listOf(alpha, beta)
        library.tracks.value = tracks
        return viewModel()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `before anything is collected the screen is loading`() =
        test {
            val viewModel =
                newViewModel()
            assertThat(viewModel.uiState.value).isEqualTo(TvHomeUiState.Loading)
        }

    @Test
    fun `rows follow the phone's sections in their order, empty ones left out`() =
        test {
            preferences.setHomeOrder(listOf(HomeSection.FavoriteAlbums, HomeSection.RecentlyAddedAlbums))
            val viewModel = withLibrary()

            val rows = (viewModel.uiState.value as TvHomeUiState.Content).rows
            assertThat(rows.take(2).map { it.section })
                .containsExactly(HomeSection.FavoriteAlbums, HomeSection.RecentlyAddedAlbums)
                .inOrder()
            assertThat((rows[0].items as HomeItems.Albums).albums).containsExactly(beta)
            assertThat((rows[1].items as HomeItems.Albums).albums).containsExactly(beta, alpha).inOrder()
            // No playlists: that section is empty and left out, as on the phone.
            assertThat(rows.map { it.section }).doesNotContain(HomeSection.FavoritePlaylists)
        }

    @Test
    fun `an empty library shows the sync, its failure, or that the server has no music`() =
        test {
            val viewModel = viewModel(SyncStatus.Running(SyncStep.Albums, 120, 900))
            assertThat(viewModel.uiState.value).isEqualTo(TvHomeUiState.Syncing(120, 900))

            sync.status.value = SyncStatus.Failed(MqError.Network, null)
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvHomeUiState.Error(MqError.Network))

            sync.status.value = SyncStatus.Idle(day(10))
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvHomeUiState.Empty)

            // Retry from those states.
            viewModel.onAction(TvHomeAction.Sync)
            runCurrent()
            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `before the first sync starts the screen shows the sync without numbers`() =
        test {
            assertThat(viewModel(SyncStatus.Idle(null)).uiState.value).isEqualTo(TvHomeUiState.Syncing())
        }

    @Test
    fun `the menu plays, shuffles and queues a cover's tracks`() =
        test {
            val viewModel = withLibrary()
            val target = TvHomeTarget.Album(alpha.id)

            viewModel.onAction(TvHomeAction.Play(target))
            viewModel.onAction(TvHomeAction.Play(target, shuffle = true))
            viewModel.onAction(TvHomeAction.AddToQueue(TvHomeTarget.Song(tracks[2])))
            runCurrent()

            assertThat(playback.plays.map { it.queue to it.shuffle })
                .containsExactly(tracks.take(2) to false, tracks.take(2) to true)
                .inOrder()
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[2]))
        }

    @Test
    fun `a track plays its row from it, a mix plays its tracks`() =
        test {
            val viewModel = withLibrary()

            viewModel.onAction(TvHomeAction.PlayTracks(tracks, 1))
            viewModel.onAction(TvHomeAction.PlayMix(Mix.Tracks))
            runCurrent()

            assertThat(playback.plays.first().startIndex).isEqualTo(1)
            assertThat(playback.plays.last().queue).containsExactlyElementsIn(tracks)
        }

    @Test
    fun `an artist plays its albums one after the other, a playlist its entries`() =
        test {
            val viewModel = withLibrary()
            library.artistAlbums.value = mapOf(ada.id to listOf(alpha, beta))
            playlists.items.value = mapOf(id("pl") to listOf(PlaylistItem("e1", 0, tracks[2])))

            viewModel.onAction(TvHomeAction.Play(TvHomeTarget.Artist(ada.id)))
            viewModel.onAction(TvHomeAction.AddToQueue(TvHomeTarget.Playlist(id("pl"))))
            runCurrent()

            assertThat(playback.plays.single().queue).containsExactlyElementsIn(tracks).inOrder()
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[2]))
        }

    @Test
    fun `the album mix plays every album whole`() =
        test {
            val viewModel = withLibrary()

            viewModel.onAction(TvHomeAction.PlayMix(Mix.Albums))
            runCurrent()

            assertThat(playback.plays.single().queue).containsExactlyElementsIn(tracks)
        }
}
