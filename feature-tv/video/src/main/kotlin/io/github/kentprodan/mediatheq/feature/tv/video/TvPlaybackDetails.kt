package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.playback.AudioPath
import io.github.kentprodan.mediatheq.core.playback.EngineDetails
import io.github.kentprodan.mediatheq.core.playback.VideoPlayback
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * What the engine decided for the playing video (#670, Settings -> Video -> Playback details): the video and its
 * decoder, the frame rate stated, measured and on the display, the audio and how it leaves, dropped frames,
 * recoveries, the buffer ahead and the subtitles. For explaining what a manual test saw.
 */
@Composable
internal fun TvPlaybackDetails(
    details: EngineDetails,
    video: VideoPlayback?,
    player: Player?,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var aheadMs by remember { mutableLongStateOf(0L) }
    var displayHz by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            aheadMs = player?.let { it.bufferedPosition - it.currentPosition } ?: 0L
            displayHz = ((view.display?.mode?.refreshRate ?: 0f) * 1000).toLong()
            delay(1_000)
        }
    }
    val lines =
        listOfNotNull(
            video?.let {
                stringResource(R.string.tv_player_details_video) + ": " +
                    listOfNotNull(
                        it.mimeType?.substringAfter('/'),
                        "${it.width}×${it.height}",
                        it.range.name,
                        it.playMethod?.name,
                        it.decoder,
                    ).joinToString(" · ")
            },
            video?.let {
                stringResource(R.string.tv_player_details_rate) + ": " +
                    listOfNotNull(
                        it.frameRate?.let { rate -> "%.3f".format(rate) },
                        it.cadenceRate?.let { rate ->
                            stringResource(R.string.tv_player_details_measured, "%.2f".format(rate))
                        },
                        stringResource(R.string.tv_player_details_display, "%.2f".format(displayHz / 1000f)),
                    ).joinToString(" · ")
            },
            stringResource(R.string.tv_player_details_audio) + ": " +
                listOfNotNull(details.audioFormat, details.audioDecoder).joinToString(" · ") + " → " +
                stringResource(details.audioPath.label) + details.passthroughEncoding?.let { " $it" }.orEmpty(),
            stringResource(R.string.tv_player_details_frames, details.droppedFrames, details.recoveries),
            stringResource(R.string.tv_player_details_buffer, aheadMs / 1000),
            stringResource(R.string.tv_player_details_subtitles) + ": " +
                (
                    details.subtitle?.let { sub ->
                        listOfNotNull(
                            sub,
                            "libass".takeIf { details.subtitleLibass },
                            details.subtitleDelayMs.takeIf { it != 0L }?.let { delayLabel(it) },
                        ).joinToString(" · ")
                    } ?: stringResource(R.string.tv_movie_subtitles_off)
                ),
        )
    Column(
        modifier
            .widthIn(max = 720.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White) }
    }
}

@Composable
private fun delayLabel(ms: Long): String {
    val seconds = "%.1f".format(abs(ms) / 1000.0)
    return if (ms > 0) {
        stringResource(R.string.tv_player_subtitles_later_by, seconds)
    } else {
        stringResource(R.string.tv_player_subtitles_earlier_by, seconds)
    }
}

private val AudioPath.label: Int
    get() =
        when (this) {
            AudioPath.Pcm -> R.string.tv_player_details_pcm
            AudioPath.Offload -> R.string.tv_player_details_offload
            AudioPath.BitPerfect -> R.string.tv_player_details_bit_perfect
            AudioPath.Passthrough -> R.string.tv_player_details_passthrough
        }
