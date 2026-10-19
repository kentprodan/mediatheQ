package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** "Disc 2" above a disc's tracks on multi-disc albums; a heading for TalkBack navigation. */
@Composable
fun DiscHeader(
    discNumber: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.core_ui_disc, discNumber),
        modifier
            .padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.l,
                top = MediatheqSpacing.l,
                bottom = MediatheqSpacing.xs,
            ).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
