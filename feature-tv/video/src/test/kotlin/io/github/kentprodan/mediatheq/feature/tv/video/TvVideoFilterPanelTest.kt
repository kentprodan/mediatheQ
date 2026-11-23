package io.github.kentprodan.mediatheq.feature.tv.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV; the grid gets fixed state, the filter actions change it as the view model would.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvVideoFilterPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val posters = (1..4).map { TvPoster(MediaId(ServerId("s"), "m$it"), "Movie $it") }

    @Test
    fun `several genres stay picked on their page, Back returns to the filters in the same panel (#579, #570)`() {
        var filter by mutableStateOf(TvVideoFilter())
        composeRule.setContent {
            MediatheqTvTheme {
                TvVideoGridScreen(
                    TvVideoKind.Movies,
                    TvVideoGridUiState.Content(
                        posters,
                        emptyList(),
                        TvVideoSort.Name,
                        filter,
                        listOf("Crime", "Drama", "Sci-Fi"),
                        listOf(1990),
                        4,
                    ),
                    { action ->
                        if (action is TvVideoGridAction.ToggleGenre) {
                            filter = filter.copy(genres = action.genre?.let { filter.genres.xor(it) } ?: emptySet())
                        }
                    },
                    {},
                )
            }
        }
        composeRule.onNodeWithText("Filter").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Genre").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Any").assertIsFocused()

        composeRule.onNodeWithText("Crime").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Drama").performSemanticsAction(SemanticsActions.OnClick)

        // The page stays open with both ticked.
        assertThat(filter.genres).containsExactly("Crime", "Drama")
        composeRule.onNodeWithText("Crime").assertIsOn()
        composeRule.onNodeWithText("Drama").assertIsOn()
        // Back in the dialog window, as the remote sends it.
        Espresso.pressBack()
        composeRule.onNodeWithText("Crime, Drama").assertExists()
        composeRule.onNode(isDialog()).assertExists()
    }

    private fun Set<String>.xor(value: String) = if (value in this) this - value else this + value
}
