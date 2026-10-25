package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorScheme
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlin.time.Duration.Companion.seconds

private val words =
    listOf(
        "Morning light across the river",
        "Every window wide awake",
        "",
        "I keep walking, never hurry",
        "Counting steps along the lake",
        "Hold the moment, let it linger",
        "Sing it once and sing it slow",
    )

private val synced = Lyrics(words.mapIndexed { i, text -> LyricLine(text, (i * 4).seconds) })

private val plain = Lyrics(words.filter { it.isNotEmpty() }.map { LyricLine(it) })

@Composable
private fun Screen(state: LyricsUiState) {
    // 13 s: the fourth line ("I keep walking") is being sung.
    LyricsScreen(state, position = { 13.seconds }, canSeek = true, onAction = {}, onClose = {})
}

/** Synced: the line being sung is marked; an empty line is a music break. */
@PreviewTest
@MqPreviews
@Composable
fun LyricsSyncedScreenshot() {
    MediatheqTheme { Screen(LyricsUiState.Content(synced)) }
}

/** Inside the player: cover-derived colors (a fixed seed stands in for a cover). */
@PreviewTest
@MqPreviews
@Composable
fun LyricsCoverColorsScreenshot() {
    MediatheqTheme {
        val dark = MaterialTheme.colorScheme.surface.red < 0.5f
        MqCoverTheme(coverColorScheme(PREVIEW_COVER_SEED, isDark = dark)) { Screen(LyricsUiState.Content(synced)) }
    }
}

/** Plain: no line is marked. */
@PreviewTest
@MqPreviews
@Composable
fun LyricsPlainScreenshot() {
    MediatheqTheme { Screen(LyricsUiState.Content(plain)) }
}

@PreviewTest
@MqPreviews
@Composable
fun LyricsNoneScreenshot() {
    MediatheqTheme { Screen(LyricsUiState.None) }
}

@PreviewTest
@MqPreviews
@Composable
fun LyricsLoadingScreenshot() {
    MediatheqTheme { Screen(LyricsUiState.Loading) }
}

@PreviewTest
@MqPreviews
@Composable
fun LyricsErrorScreenshot() {
    MediatheqTheme { Screen(LyricsUiState.Error(MqError.Network)) }
}

private const val PREVIEW_COVER_SEED = 0xFFC2185B.toInt()
