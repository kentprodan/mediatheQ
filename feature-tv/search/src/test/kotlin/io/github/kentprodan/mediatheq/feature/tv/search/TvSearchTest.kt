package io.github.kentprodan.mediatheq.feature.tv.search

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class TvSearchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val artist = Artist(id("ar"), "Moșoi")
    private val album = Album(id("al"), "Moșoi live", listOf(artist))
    private val track = Track(id("t1"), "Moșoi song", listOf(artist), 3.minutes)
    private val other = Track(id("t2"), "Moșoi again", listOf(artist), 3.minutes)
    private val playlist = Playlist(id("pl"), "Moșoi mix")

    private val library =
        FakeLibraryRepository().apply {
            albums.value = listOf(album)
            albumArtists.value = listOf(artist)
            tracks.value = listOf(track, other)
            playlists.value = listOf(playlist)
        }
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()

    private fun TestScope.viewModel(): TvSearchViewModel {
        val viewModel = TvSearchViewModel(library, preferences, TrackTaps(playback, preferences))
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        return viewModel
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `an empty field lists the recent searches`() =
        test {
            preferences.recentSearches.value = listOf("boards", "moderat")

            assertThat(viewModel().uiState.value).isEqualTo(TvSearchUiState.Recent(listOf("boards", "moderat")))
        }

    @Test
    fun `typing searches after a short pause, once, and shows the four rows`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(TvSearchAction.QueryChanged("Mo"))
            viewModel.onAction(TvSearchAction.QueryChanged("Moșoi"))
            advanceTimeBy(40)
            assertThat(library.searches).isEmpty()
            advanceTimeBy(20)
            runCurrent()

            assertThat(library.searches).containsExactly("Moșoi")
            assertThat(viewModel.uiState.value)
                .isEqualTo(
                    TvSearchUiState.Results(listOf(album), listOf(artist), listOf(track, other), listOf(playlist)),
                )
            assertThat(viewModel.query.value).isEqualTo("Moșoi")
        }

    @Test
    fun `nothing matching is no results, also when only a genre matches`() =
        test {
            library.genres.value = listOf(Genre(id("g"), "Jazz"))
            val viewModel = viewModel()

            viewModel.onAction(TvSearchAction.QueryChanged("zzz"))
            advanceTimeBy(100)
            assertThat(viewModel.uiState.value).isEqualTo(TvSearchUiState.NoResults)

            viewModel.onAction(TvSearchAction.QueryChanged("Jazz"))
            advanceTimeBy(100)
            assertThat(viewModel.uiState.value).isEqualTo(TvSearchUiState.NoResults)
        }

    @Test
    fun `a failing search shows the error`() =
        test {
            library.searchError = MqError.Storage
            val viewModel = viewModel()

            viewModel.onAction(TvSearchAction.QueryChanged("Moșoi"))
            advanceTimeBy(100)

            assertThat(viewModel.uiState.value).isEqualTo(TvSearchUiState.Error(MqError.Storage))
        }

    @Test
    fun `the Search key runs a failed search again`() =
        test {
            library.searchError = MqError.Storage
            val viewModel = viewModel()
            viewModel.onAction(TvSearchAction.QueryChanged("Moșoi"))
            advanceTimeBy(100)

            library.searchError = null
            viewModel.onAction(TvSearchAction.Submit)
            advanceTimeBy(100)

            assertThat(library.searches).containsExactly("Moșoi", "Moșoi")
            assertThat(viewModel.uiState.value).isInstanceOf(TvSearchUiState.Results::class.java)
        }

    @Test
    fun `a recent search fills the field, searches and moves to the top of the recents`() =
        test {
            preferences.recentSearches.value = listOf("boards", "Moșoi")
            val viewModel = viewModel()

            viewModel.onAction(TvSearchAction.PickRecent("Moșoi"))
            advanceTimeBy(100)

            assertThat(viewModel.query.value).isEqualTo("Moșoi")
            assertThat(viewModel.uiState.value).isInstanceOf(TvSearchUiState.Results::class.java)
            assertThat(preferences.recentSearches.value.first()).isEqualTo("Moșoi")
        }

    @Test
    fun `a selected song plays the Songs row from it and the query is kept`() =
        test {
            val viewModel = viewModel()
            viewModel.onAction(TvSearchAction.QueryChanged(" Moșoi "))
            advanceTimeBy(100)

            viewModel.onAction(TvSearchAction.PlayTrack(1))
            runCurrent()

            assertThat(playback.plays.single().queue).containsExactly(track, other).inOrder()
            assertThat(playback.plays.single().startIndex).isEqualTo(1)
            assertThat(preferences.recentSearches.value).containsExactly("Moșoi")
        }

    @Test
    fun `opening a result or the Search key keeps the query, an empty one is not kept`() =
        test {
            val viewModel = viewModel()
            viewModel.onAction(TvSearchAction.Submit)
            runCurrent()
            assertThat(preferences.recentSearches.value).isEmpty()

            viewModel.onAction(TvSearchAction.QueryChanged("live"))
            viewModel.onAction(TvSearchAction.Opened)
            viewModel.onAction(TvSearchAction.QueryChanged("mix"))
            viewModel.onAction(TvSearchAction.Submit)
            runCurrent()

            assertThat(preferences.recentSearches.value).containsExactly("mix", "live").inOrder()
        }

    // The screen.

    private val results = TvSearchUiState.Results(listOf(album), listOf(artist), listOf(track), listOf(playlist))

    @Test
    fun `focus starts in the field, Down enters the first row, Back returns to the field`() {
        var opened = false
        composeRule.setContent {
            MediatheqTvTheme {
                TvSearchScreen("Moșoi", results, {}, TvSearchNavigation(onOpenAlbum = { opened = true }))
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search albums, artists, songs and playlists").assertExists()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Moșoi live").assertIsFocused()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertThat(opened).isFalse()
    }

    @Test
    fun `Back on the field is left to the shell`() {
        var state by mutableStateOf<TvSearchUiState>(TvSearchUiState.Recent(listOf("boards")))
        composeRule.setContent { MediatheqTvTheme { TvSearchScreen("", state, {}, TvSearchNavigation()) } }
        composeRule.waitForIdle()

        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()
        composeRule.onNodeWithText("boards").requestFocus()
        composeRule.waitForIdle()
        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()
    }
}
