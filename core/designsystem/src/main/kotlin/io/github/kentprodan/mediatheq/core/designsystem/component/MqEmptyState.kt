package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The one empty / error / offline state for every screen: icon, title, optional body, an optional action button
 * (shown when [actionLabel] is set) and an optional quieter second action under it ([secondaryActionLabel]).
 */
@Composable
fun MqEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(MediatheqSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier.size(IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null) {
            Button(onClick = onAction, Modifier.padding(top = MediatheqSpacing.s)) { Text(actionLabel) }
        }
        if (secondaryActionLabel != null) {
            TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
        }
    }
}

private val IconSize = 48.dp
