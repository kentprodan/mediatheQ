package io.github.kentprodan.mediatheq.core.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
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
) {
    val context = LocalContext.current
    val view =
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
    AndroidView(factory = { view }, modifier = modifier)
}
