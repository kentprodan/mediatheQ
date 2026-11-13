package io.github.kentprodan.mediatheq.feature.tv.artist

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistPlayAction
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

// The ViewModel with fakes; the screen on a 1080p TV with a remote (D-pad keys), plain Application.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvArtistTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ada"), "Ada", overview = "Born in a small town. ".repeat(40))
    private val old = Album(id("old"), "Old", listOf(ada), year = 1999)
    private val new = Album(id("new"), "New", listOf(ada), year = 2020)
    private val guest = Album(id("guest"), "Guest spot", emptyList(), year = 2010)

    private fun track(
        name: String,
        album: Album,
        plays: Int = 0,
    ) = Track(id(name), name, listOf(ada), 3.minutes, albumId = album.id, userData = UserData(playCount = plays))

    private val tracks = listOf(track("o1", old, plays = 2), track("n1", new, plays = 9), track("n2", new))

    private fun TestScope.viewModel(): TvArtistViewModel {
        val viewModel = TvArtistViewModel(ada.id, library, preferences, playback, TrackTaps(playback, preferences))
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TestScope.withArtist(): TvArtistViewModel {
        library.albumArtists.value = listOf(ada)
        library.artistAlbums.value = mapOf(ada.id to listOf(old, new))
        library.appearsOn.value = mapOf(ada.id to listOf(guest))
        library.artistTracks.value = mapOf(ada.id to tracks)
        return viewModel()
    }

    private fun TvArtistViewModel.content() = uiState.value as TvArtistUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `the page has the albums newest first, appears on, tracks in album order and the most played`() =
        test {
            val content = withArtist().content()

            assertThat(content.albums).containsExactly(new, old).inOrder()
            assertThat(content.appearsOn).containsExactly(guest)
            assertThat(content.tracks.map { it.name() }).containsExactly("n1", "n2", "o1").inOrder()
            assertThat(content.topTracks.map { it.name() }).containsExactly("n1", "o1").inOrder()
        }

    @Test
    fun `loading first, not found for an unknown artist, empty without music`() =
        test {
            val viewModel = TvArtistViewModel(ada.id, library, preferences, playback, TrackTaps(playback, preferences))
            assertThat(viewModel.uiState.value).isEqualTo(TvArtistUiState.Loading)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvArtistUiState.NotFound)

            library.albumArtists.value = listOf(ada)
            runCurrent()
            assertThat(viewModel.content().isEmpty).isTrue()
        }

    @Test
    fun `play all, shuffle all and a top track queue the artist's tracks`() =
        test {
            val viewModel = withArtist()

            viewModel.onAction(TvArtistAction.PlayAll)
            viewModel.onAction(TvArtistAction.ShuffleAll)
            viewModel.onAction(TvArtistAction.PlayTopTrack(1))
            runCurrent()

            val content = viewModel.content()
            assertThat(playback.plays.map { Triple(it.queue, it.startIndex, it.shuffle) })
                .containsExactly(
                    Triple(content.tracks, 0, false),
                    Triple(content.tracks, 0, true),
                    Triple(content.topTracks, 1, false),
                ).inOrder()
        }

    @Test
    fun `play all follows the artist page's play action`() =
        test {
            preferences.setArtistPage(ArtistPagePreferences(playAction = ArtistPlayAction.TopTracks))
            val viewModel = withArtist()

            viewModel.onAction(TvArtistAction.PlayAll)
            runCurrent()

            assertThat(playback.plays.single().queue).isEqualTo(viewModel.content().topTracks)
        }

    @Test
    fun `select opens and closes the biography, focus starts on Play all`() {
        val state = TvArtistUiState.Content(ada, listOf(new), emptyList(), tracks, emptyList())
        composeRule.setContent { MediatheqTvTheme { TvArtistScreen(state, {}, {}) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Play all").assertIsFocused()

        val bio = composeRule.onNodeWithText("Born in a small town.", substring = true)
        bio.assert(state("Select to read more"))
        bio.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        bio.assert(state("Select to show less"))
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        bio.assert(state("Select to read more"))
    }

    @Test
    fun `an open biography scrolls with Down until its end, then focus moves on`() {
        val state = TvArtistUiState.Content(ada, listOf(new), emptyList(), tracks, emptyList())
        composeRule.setContent { MediatheqTvTheme { TvArtistScreen(state, {}, {}) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        val bio = composeRule.onNodeWithText("Born in a small town.", substring = true)
        bio.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        // A long text: the first Down scrolls it, focus stays.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.mainClock.advanceTimeBy(1_000)
        bio.assertIsFocused()

        repeat(40) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.mainClock.advanceTimeBy(500)
        }
        composeRule.onNodeWithText("Play all").assertIsFocused()
    }

    @Test
    fun `an artist without tracks starts on the biography`() {
        val state = TvArtistUiState.Content(ada, emptyList(), emptyList(), emptyList(), emptyList())
        composeRule.setContent { MediatheqTvTheme { TvArtistScreen(state, {}, {}) } }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Born in a small town.", substring = true).assertIsFocused()
    }

    @Test
    fun `coming back from an album, focus is on its card again`() {
        val state = TvArtistUiState.Content(ada, listOf(new, old), listOf(guest), tracks, emptyList())
        var shown by mutableStateOf(true)
        composeRule.setContent {
            val holder = rememberSaveableStateHolder()
            MediatheqTvTheme {
                // The artist entry leaves the composition while the album page shows, keeping its saved state.
                if (shown) holder.SaveableStateProvider("artist") { TvArtistScreen(state, {}, {}) }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Guest spot", substring = true).performSemanticsAction(SemanticsActions.RequestFocus)

        shown = false
        composeRule.mainClock.advanceTimeBy(1_000)
        shown = true
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Guest spot", substring = true).assertIsFocused()
    }

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun Track.name() = id.itemId
}
