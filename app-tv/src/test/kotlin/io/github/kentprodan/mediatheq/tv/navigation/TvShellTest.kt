package io.github.kentprodan.mediatheq.tv.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.MoviesKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; the shell with test screens, no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val backStack = mutableStateListOf<NavKey>(HomeKey)
    private val album = AlbumKey(MediaId(ServerId("s"), "a"))
    private val unknown = mutableListOf<NavKey>()
    private var closed = 0

    private data object NoScreenKey : NavKey

    private var profiles = 0

    private fun show(
        menuStyle: TvMenuStyle = TvMenuStyle.Modal,
        userName: String? = null,
    ) {
        composeRule.setContent {
            MediatheqTvTheme {
                TvShell(
                    backStack,
                    PlayerUiModel(),
                    menuStyle = menuStyle,
                    userName = userName,
                    onProfile = { profiles++ },
                    onUnknown = { unknown += it },
                    onClose = { closed++ },
                ) { navigator ->
                    entry(HomeKey) {
                        // Two items; the second opens a detail page.
                        val focus = rememberFocusMemory()
                        Column {
                            MqTvButton("First album", {}, Modifier.focusMemory(focus, "first"))
                            MqTvButton(
                                "Second album",
                                { navigator.navigateTo(album) },
                                Modifier.focusMemory(focus, "second"),
                            )
                        }
                        LaunchedEffect(Unit) { focus.restore("first") }
                    }
                    entry(LibraryKey) { Text("Library screen") }
                    entry(album) { MqTvButton("Album page", {}) }
                }
            }
        }
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `an entry shows its screen, Back from it lands on Home, Back on Home asks to close`() {
        show()

        composeRule.onNodeWithContentDescription("Music").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Library screen").assertExists()

        pressBack()
        composeRule.onNodeWithText("First album").assertExists()
        assertThat(backStack).containsExactly(HomeKey)

        // On Home Back opens the menu (#508), then asks (#343); Close stops the music and leaves.
        pressBack()
        pressBack()
        composeRule.onNodeWithText("Close mediatheQ?").assertExists()
        composeRule.onNodeWithText("Close").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertThat(closed).isEqualTo(1)
        assertThat(backStack).containsExactly(HomeKey)
    }

    @Test
    fun `Cancel or Back in the close question keeps the app on Home`() {
        show()

        // The first Back opens the menu (#508), the next one asks.
        pressBack()
        pressBack()
        // Close is focused; the key goes to the dialog's window.
        composeRule.onNodeWithText("Close").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText("Close mediatheQ?").assertDoesNotExist()

        assertThat(closed).isEqualTo(0)
        assertThat(composeRule.activity.isFinishing).isFalse()
    }

    @Test
    fun `coming back from a detail page, focus returns to the item that opened it`() {
        show()
        composeRule.onNodeWithText("Second album").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Album page").assertExists()

        pressBack()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Second album").assertIsFocused()
    }

    @Test
    fun `focus coming left into the menu lands on the selected entry, not the one focused last (#508)`() {
        show()
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)

        // Into the menu, down to Movies without selecting it, back to the screen, into the menu again.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Home").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Movies").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Home").assertIsFocused()
    }

    @Test
    fun `Standard pushes the screen aside while the menu is open, Modal leaves it in place (#520)`() {
        fun firstAlbumLeft() = composeRule.onNodeWithText("First album").getBoundsInRoot().left

        show(TvMenuStyle.Standard)
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)
        val closed = firstAlbumLeft()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Home").assertIsFocused()
        assertThat(firstAlbumLeft()).isGreaterThan(closed)

        // Right goes back to the screen, which moves back.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("First album").assertIsFocused()
        assertThat(firstAlbumLeft()).isEqualTo(closed)
    }

    @Test
    fun `the Modal menu opens over the screen without moving it (#520)`() {
        show(TvMenuStyle.Modal)
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)
        val closed = composeRule.onNodeWithText("First album").getBoundsInRoot().left

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Home").assertIsFocused()
        assertThat(composeRule.onNodeWithText("First album").getBoundsInRoot().left).isEqualTo(closed)
    }

    @Test
    fun `Back in the Standard style also opens the menu on the selected entry (#520)`() {
        show(TvMenuStyle.Standard)
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)

        pressBack()

        composeRule.onNodeWithText("Home").assertIsFocused()
    }

    @Test
    fun `the profile entry above Search opens Who's watching (#520)`() {
        show(userName = "Ana")
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        // Up from Home: Search, then the profile.
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionUp)
        }
        composeRule.onNodeWithContentDescription("Switch profile, Ana").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertThat(profiles).isEqualTo(1)

        // Right goes back to what the screen had focused.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("First album").assertIsFocused()
    }

    @Test
    fun `Back from the screen opens the menu on the selected entry (#508)`() {
        show()
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)

        pressBack()

        composeRule.onNodeWithText("Home").assertIsFocused()
        assertThat(backStack).containsExactly(HomeKey)
    }

    @Test
    fun `the menu has no Now playing entry, the pill of what plays opens it (#573)`() {
        val playing = PlayerUiModel(item = NowPlayingItem(MediaId(ServerId("s"), "t"), "Strobe", "deadmau5"))
        composeRule.setContent {
            MediatheqTvTheme {
                TvShell(backStack, playing) { _ ->
                    entry(HomeKey) { MqTvButton("First album", {}) }
                    entry(NowPlayingKey) { Text("Now Playing screen") }
                }
            }
        }
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Now playing").assertDoesNotExist()
        composeRule.onNodeWithText("Strobe").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Now Playing screen").assertExists()
        assertThat(backStack.toList()).containsExactly(HomeKey, NowPlayingKey).inOrder()
    }

    @Test
    fun `a detail page has the whole screen, without the tabs (#515)`() {
        show()
        composeRule.onNodeWithContentDescription("Search").assertExists()
        composeRule.onNodeWithText("Second album").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText("Album page").assertExists()
        composeRule.onNodeWithContentDescription("Search").assertDoesNotExist()
    }

    @Test
    fun `an entry the app mode hides leaves the back stack with what it opened`() {
        var destinations by mutableStateOf(TvDestination.entries.toList())
        backStack += listOf(MoviesKey, album)
        composeRule.setContent {
            MediatheqTvTheme {
                TvShell(backStack, PlayerUiModel(), destinations = destinations) { _ ->
                    entry(HomeKey) { Text("Home screen") }
                    entry(MoviesKey) { Text("Movies screen") }
                    entry(album) { Text("Album page") }
                }
            }
        }
        composeRule.onNodeWithText("Album page").assertExists()

        destinations = TvDestination.shownIn(AppMode.Music)

        composeRule.onNodeWithText("Home screen").assertExists()
        assertThat(backStack.toList()).containsExactly(HomeKey)
    }

    @Test
    fun `a key without a TV screen shows a placeholder and is logged once`() {
        show()
        composeRule.runOnUiThread { backStack.add(NoScreenKey) }

        composeRule.onNodeWithText("Coming in a next version of the TV app.").assertExists()
        composeRule.runOnUiThread { backStack.add(album) }
        composeRule.onNodeWithText("Album page").assertExists()
        assertThat(unknown).containsExactly(NoScreenKey)
    }

    @Test
    fun `text on the plain background is light, not tv-material's black`() {
        var color = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            MediatheqTvTheme {
                color = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
            }
        }

        assertThat(color).isEqualTo(onSurface)
    }
}
