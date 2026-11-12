package io.github.kentprodan.mediatheq.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class HomeSectionSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<HomeSectionSettingsAction>()
    private val section = HomeSection.FavoriteAlbums
    private val albums = listOf(Album(MediaId(ServerId("s"), "a"), "Satelit", emptyList()))

    private fun show(
        display: HomeDisplay = HomeDisplay.Row,
        section: HomeSection = this.section,
        items: HomeItems = HomeItems.Albums(albums),
    ) {
        val content =
            HomeSectionSettingsUiState.Content(
                HomeSectionContent(section, section.defaultSettings.copy(display = display), items),
                copyFrom = listOf(HomeSection.RandomAlbums, HomeSection.LastPlayedAlbums),
            )
        composeRule.setContent {
            MediatheqTheme { HomeSectionSettingsScreen(section, content, { actions += it }) {} }
        }
    }

    // The page scrolls down; the preview's row scrolls sideways.
    private val page =
        hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun row(title: String) = hasText(title) and hasClickAction()

    private fun open(title: String) {
        composeRule.onNode(page).performScrollToNode(row(title))
        composeRule.onNode(row(title)).performClick()
    }

    @Test
    fun `the preview shows the section's items and a switch change is stored`() {
        show()

        composeRule.onNodeWithText("Satelit").assertExists()
        open("Show play button")

        val set = actions.single() as HomeSectionSettingsAction.Set
        assertThat(set.change(section.defaultSettings)).isEqualTo(section.defaultSettings.copy(playButton = true))
    }

    @Test
    fun `a playlist section hides the options its cards cannot draw`() {
        show(section = HomeSection.FavoritePlaylists, items = HomeItems.Playlists(emptyList()))
        composeRule.onNode(page).performScrollToNode(row("Hide if empty"))
        composeRule.onNodeWithText("Show play button").assertExists()
        composeRule.onNodeWithText("Favorite status on covers").assertDoesNotExist()
        composeRule.onNodeWithText("Offline status on covers").assertDoesNotExist()
    }

    @Test
    fun `copy from picks a section of the same kind`() {
        show()

        open("Copy from…")
        composeRule.onNodeWithText("Random albums").performClick()

        assertThat(actions).containsExactly(HomeSectionSettingsAction.CopyFrom(HomeSection.RandomAlbums))
    }

    @Test
    fun `in Carousel the card options are gone, and Grid adds the rows`() {
        show(HomeDisplay.Carousel)
        composeRule.onNode(page).performScrollToNode(row("Hide if empty"))
        composeRule.onNodeWithText("Show play button").assertDoesNotExist()
        composeRule.onNodeWithText("Rows").assertDoesNotExist()
    }

    @Test
    fun `apply to all asks first`() {
        show()

        open("Apply to all sections of this kind")
        composeRule.onNodeWithText("Apply").performClick()

        assertThat(actions).containsExactly(HomeSectionSettingsAction.ApplyToAll)
    }
}
