package io.github.kentprodan.mediatheq.feature.artist

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.ArtistAlbumSubtitle
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
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
            listOf(track.copy(userData = UserData(playCount = 3))),
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
            .containsExactly(ArtistAction.Play, ArtistAction.Shuffle, ArtistAction.PlayTopTrack(track.id, all = false))
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
                        playedTracks = emptyList(),
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

    @Test
    fun `a backdrop that cannot load gives way to the artist image`() {
        val withImages =
            content.copy(
                artist =
                    artist.copy(
                        artwork = Artwork("ar1", "p1"),
                        backdrop = Artwork("ar1", "b1", type = ImageType.Backdrop),
                    ),
            )
        composeRule.setContent { MediatheqTheme { ArtistScreen(withImages, {}, {}, {}) } }

        // No image loader can fetch an Artwork here, as offline without a cached backdrop.
        composeRule.waitUntil(
            5_000,
        ) { composeRule.onAllNodesWithTag("artist-backdrop").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag("artist-image").assertExists()
    }

    @Test
    fun `show all opens every played track when there are more than the top tracks`() {
        var opened = false
        val many = (1..3).map { track.copy(id = MediaId(ServerId("s"), "t$it"), userData = UserData(playCount = it)) }
        composeRule.setContent {
            MediatheqTheme {
                ArtistScreen(
                    content.copy(playedTracks = many, page = ArtistPagePreferences(topTracksCount = 2)),
                    {},
                    {},
                    {},
                    onOpenTopTracks = { opened = true },
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Show all"))
        composeRule.onNodeWithContentDescription("Show all").performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `a similar artist opens its page`() {
        val like = Artist(MediaId(ServerId("s"), "ar9"), "Golan")
        val opened = mutableListOf<MediaId>()
        composeRule.setContent {
            MediatheqTheme {
                ArtistScreen(content.copy(similarArtists = listOf(like)), {}, {}, {}, onOpenArtist = { opened += it })
            }
        }

        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Golan"))
        composeRule.onNodeWithText("Similar artists").assertExists()
        composeRule.onNodeWithText("Golan").performClick()

        assertThat(opened).containsExactly(like.id)
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp")
    fun `the artist's More button sits where the album page's and the track rows' do`() {
        composeRule.setContent { MediatheqTheme { ArtistScreen(content, {}, {}, {}) } }

        // 16 dp from the edge, like a track row's ⋮ (measured on the album page, density 1 here).
        val more = composeRule.onNodeWithContentDescription("More options", substring = true).fetchSemanticsNode()
        assertThat(more.boundsInRoot.right).isEqualTo(411f - 16f)
    }

    @Test
    fun `the artist's stars are in its More sheet, not in About`() {
        val actions = mutableListOf<ArtistAction>()
        composeRule.setContent { MediatheqTheme { ArtistScreen(content, { actions += it }, {}, {}) } }
        composeRule.onNodeWithContentDescription("Rate 1 star").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("More options for Subcarpați").performClick()
        composeRule.onNodeWithContentDescription("Rate 4 stars").performClick()

        assertThat(actions).containsExactly(ArtistAction.Rate(8))
    }

    @Test
    fun `album cards show the year, not the artist, unless the setting says otherwise`() {
        val dated = content.copy(albums = listOf(album.copy(year = 2019)))
        var page by mutableStateOf(dated)
        composeRule.setContent { MediatheqTheme { ArtistScreen(page, {}, {}, {}) } }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("2019"))
        composeRule.onNodeWithText("2019").assertExists()

        page = dated.copy(page = ArtistPagePreferences(albumSubtitle = ArtistAlbumSubtitle.Nothing))
        composeRule.onNodeWithText("2019").assertDoesNotExist()
    }
}
