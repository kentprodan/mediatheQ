package io.github.kentprodan.mediatheq.feature.genre

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GenreScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val album = Album(id("al1"), "Satelit", emptyList())
    private val tracks = List(12) { Track(id("t$it"), "Track $it", emptyList(), 3.minutes) }
    private val content =
        GenreUiState.Content(
            Genre(id("g"), "Rock", albumCount = 1, trackCount = 12),
            listOf(album),
            emptyList(),
            tracks,
        )

    @Test
    fun `an album opens, Show all asks for every track, and Back goes back`() {
        val actions = mutableListOf<GenreAction>()
        val opened = mutableListOf<MediaId>()
        var back = 0
        composeRule.setContent {
            MediatheqTheme { GenreScreen(content, { actions += it }, { opened += it }, {}, { back++ }) }
        }

        composeRule.onNodeWithText("Satelit").performClick()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Show all"))
        composeRule.onNodeWithText("Show all").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(opened).containsExactly(album.id)
        assertThat(actions).containsExactly(GenreAction.ShowAllTracks)
        assertThat(back).isEqualTo(1)
    }

    @Test
    fun `with every track shown there is no Show all`() {
        composeRule.setContent {
            MediatheqTheme { GenreScreen(content.copy(allTracks = true), {}, {}, {}, {}) }
        }

        composeRule.onNodeWithText("Show all").assertDoesNotExist()
    }
}
