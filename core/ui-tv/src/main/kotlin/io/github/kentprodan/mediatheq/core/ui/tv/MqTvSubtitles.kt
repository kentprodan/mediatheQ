package io.github.kentprodan.mediatheq.core.ui.tv

import android.content.Context
import android.graphics.Color
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle

/**
 * The playing video's subtitles on TV (#429, as the phone's MqSubtitles): Media3's `SubtitleView` (text and bitmap
 * cues such as PGS) with the TV's caption style and size from the system settings, fed by the player's cues.
 */
@OptIn(UnstableApi::class)
@Composable
fun MqTvSubtitles(
    player: Player?,
    modifier: Modifier = Modifier,
    /** The app's choices over the system caption style (#429); all System by default. */
    style: SubtitleStyle = SubtitleStyle(),
) {
    val context = LocalContext.current
    val view = remember(context) { SubtitleView(context) }
    LaunchedEffect(view, style) { view.apply(style) }
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

/** The system caption style with [style]'s parts on top; the size as a share of the picture or the system's. */
@OptIn(UnstableApi::class)
private fun SubtitleView.apply(style: SubtitleStyle) {
    val manager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    val system =
        manager?.takeIf { it.isEnabled }?.userStyle?.let(CaptionStyleCompat::createFromCaptionStyle)
            ?: CaptionStyleCompat.DEFAULT
    val (background, edge) =
        when (style.background) {
            SubtitleStyle.Background.System -> system.backgroundColor to system.edgeType
            SubtitleStyle.Background.None -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_NONE
            SubtitleStyle.Background.Shadow -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            SubtitleStyle.Background.Box -> BOX to CaptionStyleCompat.EDGE_TYPE_NONE
        }
    setStyle(
        CaptionStyleCompat(
            style.color.argb ?: system.foregroundColor,
            background,
            system.windowColor,
            edge,
            system.edgeColor,
            system.typeface,
        ),
    )
    style.size.fraction?.let(::setFractionalTextSize) ?: setUserDefaultTextSize()
}

/** A dark box behind the text, see-through enough to keep the picture. */
private const val BOX = 0xB3000000.toInt()
