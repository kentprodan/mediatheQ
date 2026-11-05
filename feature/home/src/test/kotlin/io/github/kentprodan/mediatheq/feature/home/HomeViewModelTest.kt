package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.mixTracks
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.testing.TestClock
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
    private val preferences = FakeLibraryPreferencesRepository()
    private val trackTaps by lazy { TrackTaps(playback, preferences) }
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()
    private val shuffle = HomeShuffle(1)

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

    private fun newViewModel() =
        HomeViewModel(
            library,
            sync,
            playback,
            trackTaps,
            userData,
            downloads,
            preferences,
            shuffle,
            TestClock(day(20)),
            mainDispatcherRule.testDispatcher,
        )

    private fun TestScope.viewModel(status: SyncStatus = SyncStatus.Idle(day(10))): HomeViewModel {
        sync.status.value = status
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TestScope.withLibrary(status: SyncStatus = SyncStatus.Idle(day(10))): HomeViewModel {
        library.albums.value = listOf(alpha, beta, gamma)
        library.tracks.value = tracks
        return viewModel(status)
    }

    private fun HomeViewModel.content() = uiState.value as HomeUiState.Content

    private fun HomeViewModel.sections() = content().sections.map { it.section }

    private fun HomeViewModel.albums(section: HomeSection) =
        (content().sections.first { it.section == section }.items as HomeItems.Albums).albums

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `before anything is collected the screen is loading`() =
        test {
            assertThat(newViewModel().uiState.value).isEqualTo(HomeUiState.Loading)
        }

    @Test
    fun `today's rows are there by default, filled as before`() =
        test {
            val viewModel = withLibrary()

            assertThat(viewModel.albums(HomeSection.LastPlayedAlbums)).containsExactly(alpha, gamma).inOrder()
            assertThat(viewModel.albums(HomeSection.RecentlyAddedAlbums)).containsExactly(beta, alpha).inOrder()
            assertThat(viewModel.albums(HomeSection.FavoriteAlbums)).containsExactly(beta)
            val favorites =
                viewModel
                    .content()
                    .sections
                    .first { it.section == HomeSection.FavoriteTracks }
                    .items
            assertThat((favorites as HomeItems.Tracks).tracks).containsExactly(tracks[2], tracks[3], tracks[4])
        }

    @Test
    fun `sections follow the stored order, hidden and empty ones are skipped`() =
        test {
            preferences.setHomeOrder(listOf(HomeSection.FavoriteAlbums, HomeSection.LastPlayedAlbums))
            preferences.setHomeHidden(setOf(HomeSection.RecentlyAddedAlbums))
            val viewModel = withLibrary()

            val shown = viewModel.sections()
            assertThat(
                shown.take(2),
            ).containsExactly(HomeSection.FavoriteAlbums, HomeSection.LastPlayedAlbums).inOrder()
            assertThat(shown).doesNotContain(HomeSection.RecentlyAddedAlbums)
            // Nothing played 90 days ago, no playlists, no artists: those sections are empty and left out.
            assertThat(
                shown,
            ).containsNoneOf(HomeSection.RediscoverAlbums, HomeSection.FavoritePlaylists, HomeSection.RandomArtists)
        }

    @Test
    fun `with every section hidden Home still shows its content, not an empty library`() =
        test {
            preferences.setHomeHidden(HomeSection.entries.toSet())

            assertThat(withLibrary().uiState.value).isEqualTo(HomeUiState.Content())
        }

    @Test
    fun `random sections keep their set until pull to refresh, which also syncs`() =
        test {
            library.albums.value = (1..30).map { album("a$it") }
            library.tracks.value = listOf(track("t"))
            val viewModel = viewModel()
            val first = viewModel.albums(HomeSection.RandomAlbums)

            library.tracks.value = listOf(track("t", favorite = true))
            assertThat(viewModel.albums(HomeSection.RandomAlbums)).isEqualTo(first)

            viewModel.onAction(HomeAction.Refresh)
            assertThat(viewModel.albums(HomeSection.RandomAlbums)).isNotEqualTo(first)
            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `pull to refresh while offline only draws new random sets`() =
        test {
            library.offline.value = true
            val viewModel = withLibrary()

            viewModel.onAction(HomeAction.Refresh)

            assertThat(sync.syncCalls).isEqualTo(0)
        }

    @Test
    fun `a running sync shows as refreshing`() =
        test {
            val viewModel = withLibrary(SyncStatus.Running(SyncStep.Albums, 0, 10))

            assertThat(viewModel.content().refreshing).isTrue()
        }

    @Test
    fun `the cached library shows while the server cannot be reached`() =
        test {
            val viewModel = withLibrary(SyncStatus.Failed(MqError.Network, lastSyncedAt = day(10)))

            assertThat(viewModel.sections()).isNotEmpty()
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
    fun `offline with nothing downloaded says so, before any sync state`() =
        test {
            library.offline.value = true

            assertThat(viewModel(SyncStatus.Failed(MqError.Network, lastSyncedAt = null)).uiState.value)
                .isEqualTo(HomeUiState.NothingDownloaded)
        }

    @Test
    fun `sync runs a sync`() =
        test {
            viewModel(SyncStatus.Failed(MqError.Network, lastSyncedAt = null)).onAction(HomeAction.Sync)

            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `track mix queues every track in order, the one played in the last 14 days last`() =
        test {
            withLibrary().onAction(HomeAction.PlayMix(Mix.Tracks))

            val play = playback.plays.single()
            assertThat(play.queue).containsExactlyElementsIn(tracks)
            assertThat(play.queue.last().title).isEqualTo("a2")
            assertThat(play.shuffle).isFalse()
        }

    @Test
    fun `mixes draw from the home seed, a new order at each tap`() =
        test {
            val tracks = (1..30).map { track("t$it") }
            library.tracks.value = tracks
            val viewModel = viewModel()

            viewModel.onAction(HomeAction.PlayMix(Mix.Tracks))
            viewModel.onAction(HomeAction.PlayMix(Mix.Tracks))
            val seeded = HomeShuffle(1)
            val expected = (1..2).map { mixTracks(Mix.Tracks, tracks, emptyList(), seeded.mixRandom(), day(20)) }

            assertThat(playback.plays.map { it.queue }).isEqualTo(expected)
            assertThat(expected[0]).isNotEqualTo(expected[1])
        }

    @Test
    fun `album mix queues every album whole`() =
        test {
            withLibrary().onAction(HomeAction.PlayMix(Mix.Albums))

            val titles =
                playback.plays
                    .single()
                    .queue
                    .map { it.title }
            assertThat(titles).containsExactly("a1", "a2", "b1", "g1")
            assertThat(titles.indexOf("a2")).isEqualTo(titles.indexOf("a1") + 1)
        }

    @Test
    fun `the shortcuts offer the library's decades and decade mix plays that decade`() =
        test {
            library.albums.value = listOf(alpha, beta.copy(year = 1995), gamma.copy(year = 2001))
            library.tracks.value = tracks
            val viewModel = viewModel()

            val shortcuts =
                viewModel
                    .content()
                    .sections
                    .first { it.section == HomeSection.Shortcuts }
                    .items
            assertThat(shortcuts).isEqualTo(HomeItems.Shortcuts(listOf(1990, 2000)))

            viewModel.onAction(HomeAction.PlayMix(Mix.Decade(1990)))
            assertThat(
                playback.plays
                    .single()
                    .queue
                    .map { it.title },
            ).containsExactly("b1")
        }

    @Test
    fun `shuffle all plays every track shuffled`() =
        test {
            withLibrary().onAction(HomeAction.ShuffleAll)

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(tracks, 0, shuffle = true))
        }

    @Test
    fun `a track of a section plays the section's tracks from it`() =
        test {
            val favorites = listOf(tracks[2], tracks[3], tracks[4])
            withLibrary().onAction(HomeAction.PlayTracks(favorites, 1))

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(favorites, 1, shuffle = false))
        }

    @Test
    fun `a track's menu queues, unlikes and downloads it`() =
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
