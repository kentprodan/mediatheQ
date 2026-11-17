package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Asks for a PIN (#434): digits only, hidden, Done submits. [error] shows under the field (a wrong PIN); typing again
 * clears it through [onEdit].
 */
@Composable
fun MqTvPinDialog(
    title: String,
    label: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    onEdit: () -> Unit = {},
) {
    var pin by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            MqTvPinContent(title, label, pin, error, Modifier.focusRequester(field), {
                pin = it.filter(Char::isDigit).take(MAX_PIN)
                onEdit()
            }) { if (pin.length >= MIN_PIN) onSubmit(pin) }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

/** The dialog's content, for screenshots (popups render blank there). */
@Composable
fun MqTvPinContent(
    title: String,
    label: String,
    pin: String,
    error: String?,
    fieldModifier: Modifier = Modifier,
    onChange: (String) -> Unit = {},
    onDone: () -> Unit = {},
) {
    Column(Modifier.width(400.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        MqTvTextField(
            pin,
            onChange,
            label,
            fieldModifier,
            password = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            onDone = onDone,
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }
}

const val MIN_PIN = 4
private const val MAX_PIN = 8
