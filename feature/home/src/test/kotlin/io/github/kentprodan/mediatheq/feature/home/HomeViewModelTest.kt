package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val sync = FakeLibrarySyncRepository()
    private val playback = FakePlaybackConnection()
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun day(n: Int) = Instant.fromEpochSeconds(0) + n.days

    private val ada = Artist(id("ar"), "Ada")

    private fun album(
        name: String,
        added: Int? = null,
        favorite: Boolean = false,
    ) = Album(id(name), name, listOf(ada), dateAdded = added?.let(::day), userData = UserData(isFavorite = favorite))

    private fun track(
        name: String,
        album: String? = null,
        played: Int? = null,
        favorite: Boolean = false,
    ) = Track(
        id(name),
        name,
        listOf(ada),
        Duration.ZERO,
        albumId = album?.let(::id),
        userData = UserData(isFavorite = favorite, lastPlayedAt = played?.let(::day)),
    )

    // Albums come by sort name, as the repository gives them.
    private val alpha = album("alpha", added = 1)
    private val beta = album("beta", added = 5, favorite = true)
    private val gamma = album("gamma")
    private val tracks =
        listOf(
            track("a1", "alpha", played = 3),
            track("a2", "alpha", played = 7),
            track("b1", "beta", favorite = true),
            track("g1", "gamma", played = 4, favorite = true),
            track("single", favorite = true),
        )

    private fun TestScope.viewModel(status: SyncStatus = SyncStatus.Idle(day(10))): HomeViewModel {
        sync.status.value = status
        val viewModel = HomeViewModel(library, sync, playback, userData, downloads, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TestScope.withLibrary(status: SyncStatus = SyncStatus.Idle(day(10))): HomeViewModel {
        library.albums.value = listOf(alpha, beta, gamma)
        library.tracks.value = tracks
        return viewModel(status)
    }

    private fun HomeViewModel.content() = uiState.value as HomeUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `before anything is collected the screen is loading`() =
        test {
            val viewModel =
                HomeViewModel(library, sync, playback, userData, downloads, mainDispatcherRule.testDispatcher)

            assertThat(viewModel.uiState.value).isEqualTo(HomeUiState.Loading)
        }

    @Test
    fun `recently played lists albums by their latest played track, unplayed albums left out`() =
        test {
            assertThat(withLibrary().content().recentlyPlayed).containsExactly(alpha, gamma).inOrder()
        }

    @Test
    fun `an album's own played date also counts`() =
        test {
            val replayed = beta.copy(userData = beta.userData.copy(lastPlayedAt = day(9)))
            library.albums.value = listOf(alpha, replayed, gamma)
            library.tracks.value = tracks

            assertThat(viewModel().content().recentlyPlayed).containsExactly(replayed, alpha, gamma).inOrder()
        }

    @Test
    fun `recently added lists albums newest first, albums without a date left out`() =
        test {
            assertThat(withLibrary().content().recentlyAdded).containsExactly(beta, alpha).inOrder()
        }

    @Test
    fun `favorites list the favorite albums and songs`() =
        test {
            val content = withLibrary().content()

            assertThat(content.favoriteAlbums).containsExactly(beta)
            assertThat(content.favoriteTracks.map { it.id.itemId }).containsExactly("b1", "g1", "single").inOrder()
        }

    @Test
    fun `rows and favorite songs are capped`() =
        test {
            library.albums.value = (1..30).map { album("al$it", added = it, favorite = true) }
            library.tracks.value = (1..30).map { track("t$it", "al$it", played = it, favorite = true) }
            val content = viewModel().content()

            assertThat(content.recentlyPlayed).hasSize(HomeViewModel.ROW_LIMIT)
            assertThat(content.recentlyAdded).hasSize(HomeViewModel.ROW_LIMIT)
            assertThat(content.favoriteAlbums).hasSize(HomeViewModel.ROW_LIMIT)
            assertThat(content.favoriteTracks).hasSize(HomeViewModel.FAVORITE_TRACKS_LIMIT)
        }

    @Test
    fun `a library with nothing played, added or liked keeps every section empty`() =
        test {
            library.albums.value = listOf(gamma)
            library.tracks.value = listOf(track("g1", "gamma"))

            assertThat(viewModel().uiState.value).isEqualTo(HomeUiState.Content())
        }

    @Test
    fun `the cached library shows while the server cannot be reached`() =
        test {
            val viewModel = withLibrary(SyncStatus.Failed(MqError.Network, lastSyncedAt = day(10)))

            assertThat(viewModel.content().recentlyAdded).isNotEmpty()
        }

    @Test
    fun `on first run, nothing synced yet, the first sync shows`() =
        test {
            assertThat(viewModel(SyncStatus.Running(SyncStep.Albums, 0, 10)).uiState.value)
                .isEqualTo(HomeUiState.FirstSync)
        }

    @Test
    fun `a failed first sync shows its error`() =
        test {
            assertThat(viewModel(SyncStatus.Failed(MqError.Network, lastSyncedAt = null)).uiState.value)
                .isEqualTo(HomeUiState.Error(MqError.Network))
        }

    @Test
    fun `a synced but empty library is empty, even when a later sync fails`() =
        test {
            val viewModel = viewModel(SyncStatus.Idle(day(10)))
            assertThat(viewModel.uiState.value).isEqualTo(HomeUiState.Empty)

            sync.status.value = SyncStatus.Running(SyncStep.Albums, 0, 10)
            sync.status.value = SyncStatus.Failed(MqError.Server(500), lastSyncedAt = null)

            assertThat(viewModel.uiState.value).isEqualTo(HomeUiState.Empty)
        }

    @Test
    fun `sync runs a sync`() =
        test {
            viewModel(SyncStatus.Failed(MqError.Network, lastSyncedAt = null)).onAction(HomeAction.Sync)

            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `shuffle all plays every track shuffled`() =
        test {
            withLibrary().onAction(HomeAction.ShuffleAll)

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(tracks, 0, shuffle = true))
        }

    @Test
    fun `a favorite song plays all favorites from it`() =
        test {
            withLibrary().onAction(HomeAction.PlayFavorite(tracks[3]))

            val favorites = listOf(tracks[2], tracks[3], tracks[4])
            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(favorites, 1, shuffle = false))
        }

    @Test
    fun `a favorite song's menu queues, unlikes and downloads it`() =
        test {
            val viewModel = withLibrary()
            val song = tracks[4]

            viewModel.onAction(HomeAction.PlayNext(song))
            viewModel.onAction(HomeAction.AddToQueue(song))
            viewModel.onAction(HomeAction.SetFavorite(song, false))
            viewModel.onAction(HomeAction.Download(song))

            assertThat(playback.playedNext).containsExactly(listOf(song))
            assertThat(playback.addedToQueue).containsExactly(listOf(song))
            assertThat(userData.calls).containsExactly("favorite:single:false")
            assertThat(downloads.downloads.value.map { it.track }).containsExactly(song)
        }
}
