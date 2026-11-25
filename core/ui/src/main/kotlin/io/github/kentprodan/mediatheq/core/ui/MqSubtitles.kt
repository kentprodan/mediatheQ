package io.github.kentprodan.mediatheq.core.ui

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView

/**
 * The playing video's subtitles (ADR 0049, #274): Media3's `SubtitleView` (text and bitmap cues such as PGS) with the
 * user's caption style and size from the system settings, fed by the player's cues.
 */
@OptIn(UnstableApi::class)
@Composable
fun MqSubtitles(
    player: Player?,
    modifier: Modifier = Modifier,
    /** The view drawing styled ASS subtitles (#635), laid over Media3's; `null` for none. */
    styledOverlay: ((Context) -> View?)? = null,
    /** The second subtitle track's cues (#635), drawn at the top of the picture. */
    secondaryCues: List<Cue> = emptyList(),
) {
    val context = LocalContext.current
    val view =
        remember(context) {
            SubtitleView(context).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        }
    val secondView =
        remember(context) {
            SubtitleView(context).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        }
    DisposableEffect(player, view) {
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
            }
        player?.addListener(listener)
        view.setCues(player?.currentCues?.cues)
        onDispose {
            player?.removeListener(listener)
            view.setCues(null)
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        styledOverlay?.let { overlay ->
            AndroidView(factory = { overlay(it) ?: View(it) }, modifier = Modifier.fillMaxSize())
        }
        if (secondaryCues.isNotEmpty()) {
            // Its own view in the top quarter: the cues sit at the bottom of their view, so above the picture's middle.
            AndroidView(
                factory = { secondView },
                update = { it.setCues(secondaryCues) },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(SECONDARY_HEIGHT),
            )
        }
    }
}

private const val SECONDARY_HEIGHT = 0.25f
