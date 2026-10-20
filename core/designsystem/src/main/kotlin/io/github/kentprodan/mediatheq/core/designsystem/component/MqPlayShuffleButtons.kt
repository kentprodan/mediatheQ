package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Play (filled) and Shuffle (tonal), half width each, for album, artist and playlist headers. */
@Composable
fun MqPlayShuffleButtons(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        Button(onClick = onPlay, Modifier.weight(1f), enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.mq_play), Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
        }
        FilledTonalButton(onClick = onShuffle, Modifier.weight(1f), enabled = enabled) {
            Icon(MqIcons.Shuffle, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(
                stringResource(R.string.mq_shuffle),
                Modifier.padding(start = ButtonDefaults.IconSpacing),
                maxLines = 1,
            )
        }
    }
}
