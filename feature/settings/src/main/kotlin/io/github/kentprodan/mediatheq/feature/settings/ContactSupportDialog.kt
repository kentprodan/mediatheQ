package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Settings -> Contact support (#151): a feature request or an issue report, opened on GitHub in the browser. */
@Composable
internal fun ContactSupportDialog(
    info: AboutInfo,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    MqDialog(
        title = stringResource(R.string.settings_contact),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) {
        ContactSupportContent { kind ->
            // No app may open links (no browser): then nothing opens rather than a crash.
            runCatching { uriHandler.openUri(supportIssueUrl(kind, info)) }
            onDismiss()
        }
    }
}

/** What the dialog holds: what is sent, then one button per kind. Stateless. */
@Composable
fun ContactSupportContent(onPick: (SupportKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
        Text(
            stringResource(R.string.settings_contact_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = { onPick(SupportKind.FeatureRequest) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_contact_feature))
        }
        FilledTonalButton(onClick = { onPick(SupportKind.Issue) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_contact_issue))
        }
    }
}
