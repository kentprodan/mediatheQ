package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** A tab in offline mode with nothing downloaded for it (#63). */
@Composable
fun NothingDownloaded(
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MqEmptyState(
        MqIcons.CloudOff,
        stringResource(R.string.core_ui_nothing_downloaded_title),
        modifier = modifier,
        body = stringResource(R.string.core_ui_nothing_downloaded_body),
        actionLabel = stringResource(R.string.core_ui_open_downloads),
        onAction = onOpenDownloads,
    )
}
