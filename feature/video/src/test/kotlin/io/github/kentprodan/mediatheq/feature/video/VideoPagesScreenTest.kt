package io.github.kentprodan.mediatheq.feature.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// Plain Application: the pages are driven by fixed state, no Hilt graph. Pixel 9 sized window.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class VideoPagesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val plays = mutableListOf<Pair<MediaId, Duration>>()

    @Test
    fun `tapping the third episode plays that episode`() {
        val season = Season(id("se1"), id("sr1"), "Season 1", 1)
        val episodes =
            (1..4).map {
                Video(
                    id("e$it"),
                    VideoKind.Episode,
                    "Episode $it",
                    seriesId = id("sr1"),
                    seasonId = season.id,
                    episodeNumber = it,
                )
            }
        composeRule.setContent {
            MediatheqTheme {
                SeriesScreen(
                    SeriesUiState.Content(Series(id("sr1"), "Show"), listOf(season), season, episodes, nextUp = null),
                    onAction = {},
                    onPlay = { episode, start -> plays += episode to start },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("3. Episode 3").performScrollTo().performClick()

        assertThat(plays).containsExactly(id("e3") to Duration.ZERO)
    }

    @Test
    fun `Resume sends the saved position, Play from start zero`() {
        val movie = Video(id("m1"), VideoKind.Movie, "Moșoi", userData = UserData(playbackPosition = 41.minutes))
        composeRule.setContent {
            MediatheqTheme {
                MovieScreen(
                    MovieUiState.Content(movie, format = null, externalSubtitles = 0, resumeAt = 41.minutes),
                    onAction = {},
                    onPlay = { plays += movie.id to it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Resume · 41:00").performClick()
        composeRule.onNodeWithText("Play from start").performClick()

        assertThat(plays).containsExactly(movie.id to 41.minutes, movie.id to Duration.ZERO).inOrder()
    }
}
