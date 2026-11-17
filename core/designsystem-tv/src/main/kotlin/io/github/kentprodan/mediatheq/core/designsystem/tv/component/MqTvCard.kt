package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A TV media card (ADR 0037): an [image] (a square cover, or a 2:3 poster with [imageAspectRatio]) with a title and an
 * optional subtitle under it, all inside the card, so focus scales and outlines them together.
 */
@Composable
fun MqTvCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Long-press of select: the item's menu. */
    onLongClick: (() -> Unit)? = null,
    imageAspectRatio: Float = 1f,
    image: @Composable () -> Unit,
) {
    Card(onClick, modifier.width(CARD_WIDTH), onLongClick = onLongClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(imageAspectRatio)) { image() }
            Column(Modifier.padding(TEXT_PADDING)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// The guideline width for five cards per screen (#507).
private val CARD_WIDTH = 124.dp
private val TEXT_PADDING = 12.dp
