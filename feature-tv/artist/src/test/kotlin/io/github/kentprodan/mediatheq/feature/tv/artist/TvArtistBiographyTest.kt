package io.github.kentprodan.mediatheq.feature.tv.artist

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

// Native graphics: the legacy mode measures no text, so a long biography would fit on one line and never scroll.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvArtistBiographyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ada"), "Ada", overview = "Born in a small town. ".repeat(120))
    private val new = Album(id("new"), "New", listOf(ada))
    private val tracks = listOf(Track(id("t"), "Song", listOf(ada), 3.minutes))

    @Test
    fun `an open biography scrolls with Down until its end, then focus moves to the buttons`() {
        val state = TvArtistUiState.Content(ada, listOf(new), emptyList(), tracks, emptyList())
        composeRule.setContent { MediatheqTvTheme { TvArtistScreen(state, {}, {}) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        val bio = composeRule.onNodeWithText("Born in a small town.", substring = true)
        bio.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.mainClock.advanceTimeBy(1_000)

        // A long text: the first Down scrolls it, focus stays.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.mainClock.advanceTimeBy(1_000)
        bio.assertIsFocused()

        // Down until focus leaves the text: it lands on a button below it.
        var presses = 0
        while (bio.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } && presses < 40) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.mainClock.advanceTimeBy(500)
            presses++
        }
        assertThat(presses).isGreaterThan(1)
        // The button under the text (the nearest below its middle).
        val focused = composeRule.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.Text]
        assertThat(focused.single().text).isAnyOf("Play all", "Shuffle all")
    }
}
