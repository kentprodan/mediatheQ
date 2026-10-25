package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Five stars. Tapping a star rates with it; tapping the current rating's star clears it. [stars] is 1-5 or `null`
 * (not rated). Filled vs outlined stars carry the value, not color alone; no haptics (plain taps).
 */
@Composable
fun MqStarRating(
    stars: Int?,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        if (stars == null) {
            stringResource(R.string.mq_not_rated)
        } else {
            pluralStringResource(R.plurals.mq_rated_stars, stars, stars)
        }
    val filledTint = MaterialTheme.colorScheme.primary
    val emptyTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.semantics { stateDescription = state }) {
        for (star in 1..MAX_STARS) {
            val filled = stars != null && star <= stars
            val label =
                if (star == stars) {
                    stringResource(R.string.mq_clear_rating)
                } else {
                    pluralStringResource(R.plurals.mq_rate_stars, star, star)
                }
            IconButton(onClick = { onRate(if (star == stars) null else star) }) {
                Icon(
                    if (filled) MqIcons.StarFilled else MqIcons.Star,
                    contentDescription = label,
                    tint = if (filled) filledTint else emptyTint,
                )
            }
        }
    }
}

private const val MAX_STARS = 5
