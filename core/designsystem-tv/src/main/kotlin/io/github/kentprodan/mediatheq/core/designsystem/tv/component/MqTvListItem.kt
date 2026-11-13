package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text

/** A TV list row (ADR 0037): headline, optional supporting text and leading icon; [selected] marks the current one. */
@Composable
fun MqTvListItem(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = { Text(headline) },
        modifier = modifier,
        enabled = enabled,
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
    )
}
