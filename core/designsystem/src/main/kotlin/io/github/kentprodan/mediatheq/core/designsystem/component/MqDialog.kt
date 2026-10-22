package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * A basic dialog: [title], optional [content] (a text field, a message), then dismiss and confirm text buttons.
 * Dismissing also happens on back and on a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        MqDialogContent(title, confirmLabel, onConfirm, dismissLabel, onDismiss, confirmEnabled, content = content)
    }
}

/** What [MqDialog] shows inside its window; on its own for screenshot tests (dialog windows render blank there). */
@Composable
fun MqDialogContent(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Surface(
        modifier.semantics { paneTitle = title },
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(Modifier.padding(MediatheqSpacing.xl), verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            // A long list scrolls inside the dialog instead of pushing the buttons out.
            Box(Modifier.weight(1f, fill = false)) { content() }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
                TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        }
    }
}
