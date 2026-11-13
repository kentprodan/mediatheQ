package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * A movie or series in a grid (#270): a 2:3 poster, then the title and [subtitle] (the year) on one line each. A
 * watched item gets a check on the poster, a series its unwatched episodes as a badge; both are also said by TalkBack.
 */
@Composable
fun MqPosterCard(
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watched: Boolean = false,
    /** Unwatched episodes of a series; `null` or 0 for no badge. */
    unplayedCount: Int? = null,
    coverModel: Any? = artwork,
) {
    val badge = unplayedCount?.takeIf { it > 0 }
    val state =
        when {
            badge != null -> pluralStringResource(R.plurals.core_ui_unwatched_episodes, badge, badge)
            watched -> stringResource(R.string.core_ui_watched)
            else -> null
        }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .then(if (state != null) Modifier.semantics { stateDescription = state } else Modifier)
            .padding(bottom = MediatheqSpacing.s),
    ) {
        Box {
            MqCover(coverModel, contentDescription = null, blurHash = artwork?.blurHash, aspectRatio = POSTER_RATIO)
            val corner = Modifier.align(Alignment.TopEnd).padding(MediatheqSpacing.s)
            if (badge != null) {
                // The card's state says it in words; the bare number would be read twice.
                Badge(corner.clearAndSetSemantics {}) { Text(badge.toString()) }
            } else if (watched) {
                // On its own disc: a check in the theme color alone vanishes on a poster of similar colors.
                Icon(
                    MqIcons.Check,
                    contentDescription = null,
                    modifier =
                        corner
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(MediatheqSpacing.xs),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            title,
            Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Width / height of a movie poster. */
private const val POSTER_RATIO = 2f / 3f
