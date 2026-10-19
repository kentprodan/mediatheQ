package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.R

/**
 * The playback seek bar with elapsed and total time under it. While the user drags, the dragged value wins over
 * incoming [progress] (0..1); [onSeek] gets the fraction once the drag ends. Disabled for streams that cannot be seeked.
 */
@Composable
fun MqSeekBar(
    progress: Float,
    elapsedLabel: String,
    totalLabel: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val description = stringResource(R.string.mq_seek)
    Column(modifier) {
        Slider(
            value = dragged ?: progress.coerceIn(0f, 1f),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let(onSeek)
                dragged = null
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
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
