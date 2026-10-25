package io.github.kentprodan.mediatheq.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
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

    private val album = Album(MediaId(ServerId("s"), "al"), "Northern Lights", emptyList())
    private val track = Track(MediaId(ServerId("s"), "t"), "Morning Light", emptyList(), Duration.ZERO)

    private fun show(state: HomeUiState) {
        composeRule.setContent {
            MediatheqTheme {
                HomeScreen(state, { actions += it }, { opened += it }, { events += "settings" })
            }
        }
    }

    @Test
    fun `empty sections have no title`() {
        show(HomeUiState.Content(recentlyAdded = listOf(album)))

        composeRule.onNodeWithText("Recently added").assertExists()
        composeRule.onAllNodesWithText("Recently played").assertCountEquals(0)
        composeRule.onAllNodesWithText("Favorite albums").assertCountEquals(0)
        composeRule.onAllNodesWithText("Favorite songs").assertCountEquals(0)
    }

    @Test
    fun `section titles are headings`() {
        show(HomeUiState.Content(recentlyAdded = listOf(album), favoriteTracks = listOf(track)))

        composeRule.onAllNodes(isHeading()).assertCountEquals(2)
    }

    @Test
    fun `shuffle all, an album and a favorite song send their actions`() {
        show(HomeUiState.Content(recentlyPlayed = listOf(album), favoriteTracks = listOf(track)))

        composeRule.onNodeWithText("Shuffle all").performClick()
        composeRule.onNodeWithText("Northern Lights").performClick()
        composeRule.onNodeWithText("Morning Light").performClick()

        assertThat(actions).containsExactly(HomeAction.ShuffleAll, HomeAction.PlayFavorite(track)).inOrder()
        assertThat(opened).containsExactly(album.id)
    }

    @Test
    fun `the labelled settings button opens Settings`() {
        show(HomeUiState.Loading)

        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(events).containsExactly("settings")
    }

    @Test
    fun `the error state retries with a sync`() {
        show(HomeUiState.Error(MqError.Network))

        composeRule.onNodeWithText("Try again").performClick()

        assertThat(actions).containsExactly(HomeAction.Sync)
    }
}
