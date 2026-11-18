package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Asks for a PIN (#443): digits only, hidden; OK or Done submits from [MIN_PIN_LENGTH] digits; [error] under it. */
@Composable
fun MqPinDialog(
    title: String,
    label: String,
    confirmLabel: String,
    dismissLabel: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    onEdit: () -> Unit = {},
) {
    var pin by rememberSaveable { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val valid = pin.length >= MIN_PIN_LENGTH
    MqDialog(title, confirmLabel, { if (valid) onSubmit(pin) }, dismissLabel, onDismiss, confirmEnabled = valid) {
        Column {
            OutlinedTextField(
                pin,
                {
                    pin = it.filter(Char::isDigit).take(MAX_PIN_LENGTH)
                    onEdit()
                },
                Modifier.focusRequester(field),
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSubmit(pin) }),
            )
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8
