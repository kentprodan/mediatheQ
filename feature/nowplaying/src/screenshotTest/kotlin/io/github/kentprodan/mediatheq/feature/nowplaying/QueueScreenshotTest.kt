package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorScheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val queue =
    listOf(
        "Strobe" to "deadmau5",
        "Ghosts 'n' Stuff" to "deadmau5, Rob Swire",
        "Moșoi" to "Subcarpați",
        "Satelit" to "Subcarpați",
        "Strobe" to "deadmau5",
    ).mapIndexed { i, (title, artist) ->
        QueueItem("e$i", MediaId(ServerId("s"), "t$i"), title, artist, duration = 3.minutes + (i * 7).seconds)
    }

@Composable
private fun Screen(
    items: List<QueueItem> = queue,
    canReorder: Boolean = true,
) {
    QueueScreen(items, currentEntryId = "e1", canReorder = canReorder, onAction = {}, onClose = {})
}

/** The queue with the playing entry marked and drag handles. */
@PreviewTest
@MqPreviews
@Composable
fun QueueScreenshot() {
    MediatheqTheme { Screen() }
}

/** Inside the player: cover-derived colors (a fixed seed stands in for a cover). */
@PreviewTest
@MqPreviews
@Composable
fun QueueCoverColorsScreenshot() {
    MediatheqTheme {
        val dark = MaterialTheme.colorScheme.surface.red < 0.5f
        MqCoverTheme(coverColorScheme(PREVIEW_COVER_SEED, isDark = dark)) { Screen() }
    }
}

/** Shuffled: no drag handles. */
@PreviewTest
@MqPreviews
@Composable
fun QueueShuffledScreenshot() {
    MediatheqTheme { Screen(canReorder = false) }
}

/** Long titles and artists ellipsize. */
@PreviewTest
@MqPreviews
@Composable
fun QueueLongTitlesScreenshot() {
    MediatheqTheme {
        Screen(
            queue.take(2) +
                queue[2].copy(
                    entryId = "long",
                    title = "Concerto for Violin and Orchestra in D major, Op. 35: III. Finale. Allegro vivacissimo",
                    artist = "Hilary Hahn, Royal Liverpool Philharmonic Orchestra, Vasily Petrenko",
                ),
        )
    }
}

private const val PREVIEW_COVER_SEED = 0xFFC2185B.toInt()
