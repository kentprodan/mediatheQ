package io.github.kentprodan.mediatheq.tv.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
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

    private data object NoScreenKey : NavKey

    private fun show(drawer: DrawerValue = DrawerValue.Closed) {
        composeRule.setContent {
            MediatheqTvTheme {
                TvShell(
                    backStack,
                    PlayerUiModel(),
                    onUnknown = { unknown += it },
                    initialDrawer = drawer,
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

    private fun pressBack() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `a drawer entry shows its screen, Back from it lands on Home, Back on Home leaves`() {
        // The drawer as it is with focus in it: open, its entries named.
        show(DrawerValue.Open)

        composeRule.onNodeWithText("Library").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Library screen").assertExists()

        pressBack()
        composeRule.onNodeWithText("First album").assertExists()
        assertThat(backStack).containsExactly(HomeKey)

        // On Home the shell has nothing to pop: the activity's own Back runs and the app leaves.
        pressBack()
        assertThat(composeRule.activity.isFinishing).isTrue()
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
    fun `focus entering the drawer lands on the selected entry, not the one focused last`() {
        show()
        composeRule.onNodeWithText("First album").performSemanticsAction(SemanticsActions.RequestFocus)

        // Into the drawer, down to Library without selecting it, back to the content, into the drawer again.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Home").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Library").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Home").assertIsFocused()
    }

    @Test
    fun `the first Left from content far down the screen lands on the selected entry too`() {
        // #342: content level with a lower entry (Now playing, Settings) took the focus there on the first entry.
        composeRule.setContent {
            MediatheqTvTheme {
                TvShell(backStack, PlayerUiModel(), onUnknown = {}) { _ ->
                    entry(HomeKey) {
                        Column {
                            Spacer(Modifier.height(360.dp))
                            MqTvButton("Low album", {})
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("Low album").performSemanticsAction(SemanticsActions.RequestFocus)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Home").assertIsFocused()
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
