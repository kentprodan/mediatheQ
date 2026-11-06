package io.github.kentprodan.mediatheq.feature.search

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

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
        onOpenGenre: (MediaId) -> Unit = {},
        onOpenLabel: (RecordLabelKey) -> Unit = {},
    ) {
        composeRule.setContent {
            MediatheqTheme {
                SearchScreen(
                    state,
                    { actions += it },
                    {},
                    onOpenArtist,
                    {},
                    textFieldState = field,
                    onOpenGenre = onOpenGenre,
                    onOpenLabel = onOpenLabel,
                )
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

    @Test
    fun `genres show as their own group and open the genre`() {
        val genre = Genre(MediaId(ServerId("s"), "g"), "Electrónica", albumCount = 2, trackCount = 9)
        var opened: MediaId? = null
        show(SearchUiState.Results(SearchResults(genres = listOf(genre))), onOpenGenre = { opened = it })

        composeRule.onNode(hasText("Genres") and isHeading()).assertExists()
        composeRule.onNodeWithText("Electrónica").performClick()

        assertThat(opened).isEqualTo(genre.id)
        assertThat(actions).contains(SearchAction.ResultOpened)
    }

    @Test
    fun `record labels show as their own group and open the label's page`() {
        val label = RecordLabel(MediaId(ServerId("s"), "l"), "Electrecord", albumCount = 2)
        var opened: RecordLabelKey? = null
        show(SearchUiState.Results(SearchResults(labels = listOf(label))), onOpenLabel = { opened = it })

        composeRule.onNodeWithText("Record labels").assertExists()
        composeRule.onNodeWithText("Electrecord").performClick()

        assertThat(opened).isEqualTo(RecordLabelKey(label.id, "Electrecord"))
        assertThat(actions).contains(SearchAction.ResultOpened)
    }

    private val ada = Artist(MediaId(ServerId("s"), "ar"), "Ada")
    private val song = Track(MediaId(ServerId("s"), "t1"), "Song", listOf(ada), Duration.ZERO)
    private val found = SearchUiState.Results(SearchResults(artists = listOf(ada), tracks = listOf(song)))

    @Test
    fun `a chip selects its kind, and a chip with nothing found is disabled`() {
        show(found)

        composeRule.onNodeWithText("Albums").assertIsNotEnabled()
        composeRule.onNode(hasText("Songs") and hasClickAction()).performClick()

        assertThat(actions.filterIsInstance<SearchAction.SelectFilter>())
            .containsExactly(SearchAction.SelectFilter(SearchFilter.Tracks))
    }

    @Test
    fun `the heart on a song favorites it`() {
        show(found)

        composeRule.onNodeWithContentDescription("Favorite Song").performClick()

        assertThat(actions.filterIsInstance<SearchAction.SetFavorite>())
            .containsExactly(SearchAction.SetFavorite(song, true))
    }

    @Test
    fun `no heart when the setting is off`() {
        show(found.copy(page = SearchPagePreferences(showFavorite = false)))

        composeRule.onNodeWithContentDescription("Favorite Song").assertDoesNotExist()
    }

    @Test
    fun `no heart while selecting`() {
        show(found.copy(selection = ListSelection<MediaId>().toggle(song.id)))

        composeRule.onNodeWithContentDescription("Favorite Song").assertDoesNotExist()
    }
}
