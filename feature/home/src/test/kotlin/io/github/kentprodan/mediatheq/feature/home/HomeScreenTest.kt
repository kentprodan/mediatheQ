package io.github.kentprodan.mediatheq.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<HomeAction>()
    private val opened = mutableListOf<MediaId>()
    private val events = mutableListOf<String>()
    private val openedSections = mutableListOf<HomeSection>()

    private val album = Album(MediaId(ServerId("s"), "al"), "Northern Lights", emptyList())
    private val track = Track(MediaId(ServerId("s"), "t"), "Morning Light", emptyList(), Duration.ZERO)

    private fun show(state: HomeUiState) {
        composeRule.setContent {
            MediatheqTheme {
                HomeScreen(
                    state,
                    { actions += it },
                    HomeNavigation(
                        onOpenAlbum = { opened += it },
                        onOpenSettings = { events += "settings" },
                        onOpenDownloads = { events += "downloads" },
                        onOpenSection = { openedSections += it },
                    ),
                )
            }
        }
    }

    private fun section(
        section: HomeSection,
        items: HomeItems,
    ) = HomeSectionContent(section, section.defaultSettings, items)

    private val sections =
        listOf(
            section(HomeSection.LastPlayedAlbums, HomeItems.Albums(listOf(album))),
            section(HomeSection.FavoriteTracks, HomeItems.Tracks(listOf(track))),
        )

    @Test
    fun `the shortcuts play a mix, and Decade mix plays the decade picked`() {
        show(HomeUiState.Content(listOf(section(HomeSection.Shortcuts, HomeItems.Shortcuts(listOf(1980, 1990))))))

        composeRule.onNodeWithText("Track mix").performClick()
        composeRule.onNodeWithText("Album mix").performClick()
        composeRule.onNodeWithText("Decade mix").performClick()
        composeRule.onNodeWithText("1990s").performClick()

        assertThat(actions)
            .containsExactly(
                HomeAction.PlayMix(Mix.Tracks),
                HomeAction.PlayMix(Mix.Albums),
                HomeAction.PlayMix(Mix.Decade(1990)),
            ).inOrder()
        composeRule.onNodeWithText("1990s").assertDoesNotExist()
    }

    @Test
    fun `without decades there is no Decade mix`() {
        show(HomeUiState.Content(listOf(section(HomeSection.Shortcuts, HomeItems.Shortcuts(emptyList())))))

        composeRule.onNodeWithText("Decade mix").assertDoesNotExist()
    }

    @Test
    fun `each section has its title as a heading, in order`() {
        show(HomeUiState.Content(sections))

        composeRule.onAllNodes(isHeading()).assertCountEquals(2)
        composeRule.onNodeWithText("Last played albums").assertExists()
        composeRule.onNodeWithText("Favorite tracks").assertExists()
    }

    @Test
    fun `shuffle all, an album and a track send their actions`() {
        show(HomeUiState.Content(sections))

        composeRule.onNodeWithText("Shuffle all").performClick()
        composeRule.onNodeWithText("Northern Lights").performClick()
        composeRule.onNodeWithText("Morning Light").performClick()

        assertThat(actions).containsExactly(HomeAction.ShuffleAll, HomeAction.PlayTracks(listOf(track), 0)).inOrder()
        assertThat(opened).containsExactly(album.id)
    }

    @Test
    fun `a section header opens its full list, with or without the more indicator, and the shortcuts have none`() {
        val noMore =
            HomeSectionContent(
                HomeSection.FavoriteAlbums,
                HomeSection.FavoriteAlbums.defaultSettings.copy(moreIndicator = false),
                HomeItems.Albums(listOf(album)),
            )
        show(
            HomeUiState.Content(
                listOf(
                    section(HomeSection.Shortcuts, HomeItems.Shortcuts(emptyList())),
                    section(HomeSection.FavoriteTracks, HomeItems.Tracks(listOf(track))),
                    noMore,
                ),
            ),
        )

        composeRule.onNode(hasText("Favorite tracks") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Favorite albums") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Shortcuts") and hasClickAction()).assertDoesNotExist()

        assertThat(openedSections).containsExactly(HomeSection.FavoriteTracks, HomeSection.FavoriteAlbums)
    }

    @Test
    fun `a section without its header shows only its items`() {
        val bare =
            HomeSectionContent(
                HomeSection.FavoriteAlbums,
                HomeSection.FavoriteAlbums.defaultSettings.copy(showHeader = false),
                HomeItems.Albums(listOf(album)),
            )
        show(HomeUiState.Content(listOf(bare)))

        composeRule.onAllNodes(isHeading()).assertCountEquals(0)
        composeRule.onNodeWithText("Northern Lights").assertExists()
    }

    @Test
    fun `the labelled settings button opens Settings`() {
        show(HomeUiState.Loading)

        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(events).containsExactly("settings")
    }

    @Test
    fun `nothing downloaded opens the Downloads tab`() {
        show(HomeUiState.NothingDownloaded)

        composeRule.onNodeWithText("Open Downloads").performClick()

        assertThat(events).containsExactly("downloads")
    }

    @Test
    fun `the error state retries with a sync`() {
        show(HomeUiState.Error(MqError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(HomeAction.Sync)
    }
}
