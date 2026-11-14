package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The playback seek bar with elapsed and total time under it: the track's waveform when [levels] are known (#124),
 * else the [fallback]. While the user drags, the dragged value wins over incoming [progress] (0..1); [onSeek] gets the
 * fraction once the drag ends. Disabled for streams that cannot be seeked. With [remainingLabel] ("-3:12") the right
 * side counts down instead of the total (#125), and [center] sits between the times (Now Playing's quality label);
 * TalkBack still reads the position against the total.
 */
@Composable
fun MqSeekBar(
    progress: Float,
    elapsedLabel: String,
    totalLabel: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    levels: List<Float>? = null,
    style: MqWaveformStyle = MqWaveformStyle.Sharp,
    fallback: MqProgressFallback = MqProgressFallback.Basic,
    remainingLabel: String? = null,
    /** Chapter marks as fractions (#277). */
    marks: List<Float> = emptyList(),
    /** The dragged fraction, `null` once let go (#277). */
    onScrub: (Float?) -> Unit = {},
    // Last: callers pass it as a trailing lambda.
    center: @Composable () -> Unit = {},
) {
    Column(modifier) {
        MqWaveformProgress(
            levels = levels,
            progress = progress,
            positionText = stringResource(R.string.mq_seek_position, elapsedLabel, totalLabel),
            onSeek = onSeek,
            enabled = enabled,
            style = style,
            fallback = fallback,
            marks = marks,
            onScrub = onScrub,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                elapsedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f).padding(horizontal = MediatheqSpacing.s), contentAlignment = Alignment.Center) {
                center()
            }
            Text(
                remainingLabel ?: totalLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
