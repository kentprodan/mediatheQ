package io.github.kentprodan.mediatheq.feature.search

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SearchAction>()
    private val field = TextFieldState()

    private fun show(
        state: SearchUiState,
        onOpenArtist: (MediaId) -> Unit = {},
    ) {
        composeRule.setContent {
            MediatheqTheme {
                SearchScreen(state, { actions += it }, {}, onOpenArtist, {}, textFieldState = field)
            }
        }
    }

    @Test
    fun `tapping a recent search fills the field and searches it`() {
        show(SearchUiState.Recent(listOf("mosoi", "ada")))

        composeRule.onNodeWithText("ada").performClick()
        composeRule.waitForIdle()

        assertThat(field.text.toString()).isEqualTo("ada")
        assertThat(actions.last()).isEqualTo(SearchAction.QueryChanged("ada"))
    }

    @Test
    fun `recent searches have a heading and a clear button`() {
        show(SearchUiState.Recent(listOf("mosoi")))

        composeRule.onNode(isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Clear").performClick()

        assertThat(actions).contains(SearchAction.ClearRecent)
    }

    @Test
    fun `the clear-text button has a label and empties the field`() {
        field.edit { replace(0, length, "mosoi") }
        show(SearchUiState.Recent(emptyList()))

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.waitForIdle()

        assertThat(field.text.toString()).isEmpty()
    }

    @Test
    fun `focusing the field keeps the results on screen`() {
        val ada = Artist(MediaId(ServerId("s"), "ar"), "Ada Moșoi")
        show(SearchUiState.Results(SearchResults(artists = listOf(ada))))

        composeRule.onNodeWithText("Search your library").performClick()

        composeRule.onNodeWithText("Ada Moșoi").assertIsDisplayed()
    }

    @Test
    fun `opening a result keeps the query and navigates`() {
        val ada = Artist(MediaId(ServerId("s"), "ar"), "Ada Moșoi")
        var opened: MediaId? = null
        show(SearchUiState.Results(SearchResults(artists = listOf(ada))), onOpenArtist = { opened = it })

        composeRule.onNodeWithText("Ada Moșoi").performClick()

        assertThat(opened).isEqualTo(ada.id)
        assertThat(actions).contains(SearchAction.ResultOpened)
    }
}
