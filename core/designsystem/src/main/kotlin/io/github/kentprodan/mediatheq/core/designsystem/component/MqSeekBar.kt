package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R

/**
 * The playback seek bar with elapsed and total time under it: the track's waveform when [levels] are known (#124),
 * else the [fallback]. While the user drags, the dragged value wins over incoming [progress] (0..1); [onSeek] gets the
 * fraction once the drag ends. Disabled for streams that cannot be seeked.
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
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                elapsedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                totalLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
