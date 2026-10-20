package io.github.kentprodan.mediatheq.feature.artist

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
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

// Plain Application: the stateless screen needs no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ArtistScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val artist = Artist(MediaId(ServerId("s"), "ar1"), "Subcarpați")
    private val album = Album(MediaId(ServerId("s"), "al1"), "Satelit", listOf(artist))
    private val compilation = Album(MediaId(ServerId("s"), "al2"), "Hits", emptyList())
    private val track = Track(MediaId(ServerId("s"), "t1"), "Moșoi", listOf(artist), 3.minutes)
    private val content =
        ArtistUiState.Content(
            artist,
            listOf(album),
            listOf(compilation),
            listOf(track),
            listOf(track),
        )

    @Test
    fun `play, shuffle, a top track, an album and back do what they say`() {
        val actions = mutableListOf<ArtistAction>()
        val opened = mutableListOf<MediaId>()
        var back = false
        composeRule.setContent {
            MediatheqTheme { ArtistScreen(content, { actions += it }, { opened += it }, onBack = { back = true }) }
        }

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Shuffle").performClick()
        // The big header pushes the sections below the fold of the test screen.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Moșoi"))
        composeRule.onNodeWithText("Moșoi").performClick()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Hits"))
        composeRule.onNodeWithText("Hits").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions)
            .containsExactly(ArtistAction.Play, ArtistAction.Shuffle, ArtistAction.PlayTopTrack(track.id))
            .inOrder()
        assertThat(opened).containsExactly(compilation.id)
        assertThat(back).isTrue()
    }

    @Test
    fun `an artist with nothing in the library shows the empty state`() {
        composeRule.setContent {
            MediatheqTheme {
                ArtistScreen(
                    content.copy(
                        albums = emptyList(),
                        appearsOn = emptyList(),
                        tracks = emptyList(),
                        topTracks = emptyList(),
                    ),
                    {},
                    {},
                    {},
                )
            }
        }

        composeRule.onNodeWithText("No music yet").assertExists()
        composeRule.onNodeWithText("Play").assertDoesNotExist()
    }
}
