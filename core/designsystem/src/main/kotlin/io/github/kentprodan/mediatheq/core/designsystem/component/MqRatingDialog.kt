package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.R

/**
 * Rates one item, e.g. after a Rate swipe (#123): [title] (the item's name) over five [MqStarRating] stars; a tap on a
 * star rates (or clears) and closes, Cancel leaves the rating as it was.
 */
@Composable
fun MqRatingDialog(
    title: String,
    halfStars: Int?,
    allowHalf: Boolean,
    onRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(title, null, {}, stringResource(R.string.mq_cancel), onDismiss) {
        MqStarRating(halfStars, { onRate(it) }, allowHalf = allowHalf)
    }
}

/** [MqRatingDialog]'s content on its own, for screenshot tests (dialog windows render blank there). */
@Composable
fun MqRatingDialogContent(
    title: String,
    halfStars: Int?,
    allowHalf: Boolean,
) {
    MqDialogContent(title, null, {}, stringResource(R.string.mq_cancel), {}) {
        MqStarRating(halfStars, {}, allowHalf = allowHalf)
    }
}
