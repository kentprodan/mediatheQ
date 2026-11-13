package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A screen without items (loading, empty, error): a [title], a [body] line, an optional [detail] (a progress) and an
 * optional button, which takes focus so the remote can press it at once.
 */
@Composable
fun MqTvMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val button = remember { FocusRequester() }
    Column(
        modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        action?.let { MqTvButton(it, onAction, Modifier.focusRequester(button)) }
    }
    if (action != null) LaunchedEffect(Unit) { runCatching { button.requestFocus() } }
}
