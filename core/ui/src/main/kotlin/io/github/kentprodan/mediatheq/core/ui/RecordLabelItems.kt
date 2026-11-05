package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.RecordLabel

/** A record label in a list (#131): its first album's cover (or the label icon), name and album count. */
@Composable
fun RecordLabelRow(
    label: RecordLabel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(
        label.name,
        label.artwork,
        MqIcons.RecordLabel,
        onClick,
        modifier,
        subtitle = pluralStringResource(R.plurals.core_ui_album_count, label.albumCount, label.albumCount),
    )
}
