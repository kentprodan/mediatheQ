package io.github.kentprodan.mediatheq.feature.album

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.AlbumResume
import io.github.kentprodan.mediatheq.core.model.Artist
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
import kotlin.time.Duration.Companion.seconds

// Plain Application: the stateless screen needs no Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AlbumScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The page itself, not the row of more albums inside it.
    private val list =
        hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private val albumId = MediaId(ServerId("s"), "al1")
    private val tracks =
        listOf(
            1,
            2,
        ).map { Track(MediaId(ServerId("s"), "t$it"), "Track $it", emptyList(), 3.minutes, trackNumber = it) }
    private val content = AlbumUiState.Content(Album(albumId, "Satelit", emptyList()), listOf(Disc(1, tracks)))

    @Test
    fun `play, shuffle, a track tap and back send their actions`() {
        val actions = mutableListOf<AlbumAction>()
        var back = false
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content, { actions += it }, onBack = { back = true }) }
        }

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithContentDescription("Shuffle").performClick()
        // The big header pushes the tracks below the fold of the test screen.
        composeRule.onNode(list).performScrollToNode(hasText("Track 2"))
        composeRule.onNodeWithText("Track 2").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions)
            .containsExactly(AlbumAction.Play, AlbumAction.Shuffle, AlbumAction.PlayTrack(tracks[1].id))
            .inOrder()
        assertThat(back).isTrue()
    }

    @Test
    fun `an empty album disables play and shuffle`() {
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content.copy(discs = emptyList()), {}, onBack = {}) }
        }

        composeRule.onNodeWithText("Play").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle").assertIsNotEnabled()
    }

    @Test
    fun `the playing track is announced as now playing, the others are not`() {
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content.copy(currentTrackId = tracks[0].id), {}, onBack = {}) }
        }

        composeRule.onNode(list).performScrollToNode(hasText("Track 1"))
        composeRule.onNodeWithText("Track 1").assert(hasStateDescription("Now playing"))
        composeRule.onNode(list).performScrollToNode(hasText("Track 2"))
        composeRule.onNodeWithText("Track 2").assert(hasStateDescription("Now playing").not())
    }

    @Test
    fun `add to playlist sends one track from its menu and every track from the album menu`() {
        val added = mutableListOf<List<MediaId>>()
        composeRule.setContent {
            MediatheqTheme { AlbumScreen(albumId, content, {}, onBack = {}, onAddToPlaylist = { added += it }) }
        }

        composeRule
            .onNode(list)
            .performScrollToNode(hasContentDescription("More options for Track 2"))
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Add to playlist").performClick()
        composeRule.onNodeWithContentDescription("More options for Satelit").performClick()
        composeRule.onNodeWithText("Add to playlist").performClick()

        assertThat(added).containsExactly(listOf(tracks[1].id), tracks.map { it.id }).inOrder()
    }

    @Test
    fun `the header heart and stars and a track menu send favorite and rating`() {
        val actions = mutableListOf<AlbumAction>()
        val stars = content.copy(page = AlbumPagePreferences(showRatingBar = true))
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, stars, { actions += it }, onBack = {}) } }

        composeRule.onNode(list).performScrollToNode(hasContentDescription("Rate 4 stars"))
        composeRule.onNodeWithContentDescription("Favorite").performClick()
        composeRule.onNodeWithContentDescription("Rate 4 stars").performClick()
        composeRule
            .onNode(list)
            .performScrollToNode(hasContentDescription("More options for Track 2"))
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Add to favorites").performClick()

        assertThat(actions)
            .containsExactly(
                AlbumAction.SetFavorite(albumId, true),
                AlbumAction.Rate(8),
                AlbumAction.SetFavorite(tracks[1].id, true),
            ).inOrder()
    }

    @Test
    fun `with ratings off only the heart shows`() {
        val off = content.copy(ratingMode = RatingMode.Off, page = AlbumPagePreferences(showRatingBar = true))
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, off, {}, onBack = {}) } }

        composeRule.onNodeWithContentDescription("Favorite").assertExists()
        composeRule.onNodeWithContentDescription("Rate 1 star").assertDoesNotExist()
    }

    @Test
    fun `with half stars the current star gives a half star`() {
        val actions = mutableListOf<AlbumAction>()
        val rated =
            content.copy(
                album = content.album.copy(userData = UserData(rating = 8.0)),
                ratingMode = RatingMode.HalfStars,
                page = AlbumPagePreferences(showRatingBar = true),
            )
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, rated, { actions += it }, onBack = {}) } }

        composeRule.onNode(list).performScrollToNode(hasContentDescription("Rate 3.5 stars"))
        composeRule.onNodeWithContentDescription("Rate 3.5 stars").performClick()

        assertThat(actions).containsExactly(AlbumAction.Rate(7))
    }

    @Test
    fun `the stars are off by default`() {
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, content, {}, onBack = {}) } }

        composeRule.onNodeWithContentDescription("Rate 1 star").assertDoesNotExist()
    }

    @Test
    fun `resume shows only with somewhere to resume, and sends its action`() {
        val actions = mutableListOf<AlbumAction>()
        composeRule.setContent {
            MediatheqTheme {
                AlbumScreen(
                    albumId,
                    content.copy(resume = AlbumResume(tracks[1].id, 30.seconds)),
                    { actions += it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Resume").performClick()

        assertThat(actions).containsExactly(AlbumAction.Resume)
    }

    @Test
    fun `without a resume point there is no Resume button`() {
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, content, {}, onBack = {}) } }

        composeRule.onNodeWithText("Resume").assertDoesNotExist()
    }

    @Test
    fun `hidden buttons move to the More menu`() {
        val actions = mutableListOf<AlbumAction>()
        val hidden =
            content.copy(
                page =
                    AlbumPagePreferences(
                        hiddenButtons = setOf(AlbumButton.Shuffle, AlbumButton.PlayNext, AlbumButton.Favorite),
                    ),
            )
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, hidden, { actions += it }, onBack = {}) } }

        composeRule.onNodeWithContentDescription("Shuffle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play next").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Add to queue").assertExists()
        composeRule.onNodeWithContentDescription("More options for Satelit").performClick()
        composeRule.onNodeWithText("Shuffle").performClick()
        composeRule.onNodeWithContentDescription("More options for Satelit").performClick()
        composeRule.onNodeWithText("Play next").performClick()
        composeRule.onNodeWithContentDescription("More options for Satelit").performClick()
        composeRule.onNodeWithText("Add to favorites").performClick()

        assertThat(actions)
            .containsExactly(AlbumAction.Shuffle, AlbumAction.PlayNext(), AlbumAction.SetFavorite(albumId, true))
            .inOrder()
    }

    @Test
    fun `an album artist and an album of more from the artist open their pages`() {
        val artist = Artist(MediaId(ServerId("s"), "ar1"), "Subcarpați")
        val other = Album(MediaId(ServerId("s"), "al2"), "Underground", listOf(artist))
        val more = content.copy(album = content.album.copy(albumArtists = listOf(artist)), moreAlbums = listOf(other))
        val opened = mutableListOf<MediaId>()
        composeRule.setContent {
            MediatheqTheme {
                AlbumScreen(albumId, more, {}, onBack = {}, onOpenArtist = { opened += it }, onOpenAlbum = {
                    opened +=
                        it
                })
            }
        }

        composeRule.onNodeWithText("Subcarpați").performClick()
        composeRule.onNode(list).performScrollToNode(hasText("Underground"))
        composeRule.onNodeWithText("More from Subcarpați").assertExists()
        composeRule.onNodeWithText("Underground").performClick()

        assertThat(opened).containsExactly(artist.id, other.id).inOrder()
    }

    @Test
    fun `about shows the overview and the facts the album has`() {
        val about =
            content.copy(
                album = content.album.copy(overview = "A long story about Satelit."),
                genres = listOf("Hip-Hop"),
            )
        composeRule.setContent { MediatheqTheme { AlbumScreen(albumId, about, {}, onBack = {}) } }

        composeRule.onNode(list).performScrollToNode(hasText("Hip-Hop"))
        composeRule.onNodeWithText("About").assertExists()
        composeRule.onNodeWithText("A long story about Satelit.").assertExists()
        composeRule.onNodeWithText("Release date").assertDoesNotExist()
    }
}
