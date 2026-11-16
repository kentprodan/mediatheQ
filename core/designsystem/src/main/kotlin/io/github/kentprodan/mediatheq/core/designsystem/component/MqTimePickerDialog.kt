package io.github.kentprodan.mediatheq.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * A clock-face time picker in a dialog (#378), in the phone's 12 or 24 hour format; [onConfirm] gets the hour (0-23)
 * and minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    BasicAlertDialog(onDismissRequest = onDismiss) {
        MqTimePickerContent(
            title,
            initialHour,
            initialMinute,
            is24Hour,
            confirmLabel,
            dismissLabel,
            onConfirm,
            onDismiss,
        )
    }
}

/** What [MqTimePickerDialog] shows inside its window; on its own for screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqTimePickerContent(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour)
    MqDialogContent(title, confirmLabel, { onConfirm(state.hour, state.minute) }, dismissLabel, onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) }
    }
}
