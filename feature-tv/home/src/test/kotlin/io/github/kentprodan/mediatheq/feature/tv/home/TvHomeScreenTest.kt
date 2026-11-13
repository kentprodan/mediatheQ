package io.github.kentprodan.mediatheq.feature.tv.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; plain Application, the screen gets fixed state. A remote, not a finger: D-pad keys.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvHomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ar"), "Ada")
    private val albums = (1..6).map { Album(id("al$it"), "Album $it", listOf(ada)) }
    private val favorites = (1..6).map { Album(id("fa$it"), "Favorite $it", listOf(ada)) }
    private val actions = mutableListOf<TvHomeAction>()
    private val opened = mutableListOf<MediaId>()

    private fun show() {
        val state =
            TvHomeUiState.Content(
                listOf(
                    TvHomeRow(HomeSection.RecentlyAddedAlbums, HomeItems.Albums(albums)),
                    TvHomeRow(HomeSection.FavoriteAlbums, HomeItems.Albums(favorites)),
                ),
            )
        composeRule.setContent {
            MediatheqTvTheme {
                TvHomeScreen(
                    state,
                    { actions += it },
                    TvHomeNavigation(onOpenAlbum = { opened += it }),
                )
            }
        }
    }

    private fun press(keys: List<Key>) = composeRule.onRoot().performKeyInput { keys.forEach { pressKey(it) } }

    @Test
    fun `Home opens on the first card, D-pad right moves to the next, select opens the album`() {
        show()

        composeRule.onNodeWithText("Album 1").assertIsFocused()
        press(listOf(Key.DirectionRight))
        composeRule.onNodeWithText("Album 2").assertIsFocused()

        press(listOf(Key.DirectionCenter))
        assertThat(opened).containsExactly(id("al2"))
    }

    @Test
    fun `going down a row and back up returns to the same card`() {
        show()

        press(listOf(Key.DirectionRight, Key.DirectionRight))
        composeRule.onNodeWithText("Album 3").assertIsFocused()
        press(listOf(Key.DirectionDown))
        composeRule.onNodeWithText("Album 3").assertIsNotFocused()
        press(listOf(Key.DirectionUp))

        composeRule.onNodeWithText("Album 3").assertIsFocused()
    }

    @Test
    fun `the menu key opens Play, Shuffle and Add to queue, and Play plays the album`() {
        show()

        press(listOf(Key.Menu))
        listOf("Play", "Shuffle", "Add to queue").forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithText("Play").assertIsFocused()

        // The menu is a dialog, its own window: the key goes to it.
        composeRule.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionCenter) }
        assertThat(actions).containsExactly(TvHomeAction.Play(TvHomeTarget.Album(id("al1"))))
        composeRule.onNodeWithText("Shuffle").assertDoesNotExist()
    }
}
