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
import kotlin.math.roundToInt

/**
 * Five stars. [halfStars] is the rating in half stars, 1-10, or `null` (not rated). Tapping a star rates with it;
 * tapping the current rating's star clears it, or with [allowHalf] first makes it a half star. Filled, half and
 * outlined stars carry the value, not color alone; each star says what a tap does; no haptics (plain taps).
 */
@Composable
fun MqStarRating(
    halfStars: Int?,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowHalf: Boolean = false,
) {
    val shown = if (allowHalf) halfStars else wholeHalfStars(halfStars)
    val state =
        when {
            shown == null -> stringResource(R.string.mq_not_rated)
            shown % 2 == 1 -> stringResource(R.string.mq_rated_half_stars, halfLabel(shown))
            else -> pluralStringResource(R.plurals.mq_rated_stars, shown / 2, shown / 2)
        }
    val filledTint = MaterialTheme.colorScheme.primary
    val emptyTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.semantics { stateDescription = state }) {
        for (star in 1..MAX_STARS) {
            val next = starTap(halfStars, star, allowHalf)
            val label =
                when {
                    next == null -> stringResource(R.string.mq_clear_rating)
                    next % 2 == 1 -> stringResource(R.string.mq_rate_half_stars, halfLabel(next))
                    else -> pluralStringResource(R.plurals.mq_rate_stars, star, star)
                }
            val fill = starFill(shown, star)
            IconButton(onClick = { onRate(next) }) {
                Icon(
                    when (fill) {
                        StarFill.Full -> MqIcons.StarFilled
                        StarFill.Half -> MqIcons.StarHalf
                        StarFill.Empty -> MqIcons.Star
                    },
                    contentDescription = label,
                    tint = if (fill == StarFill.Empty) emptyTint else filledTint,
                )
            }
        }
    }
}

internal enum class StarFill { Empty, Half, Full }

/** How [star] (1-5) is drawn for [halfStars]. */
internal fun starFill(
    halfStars: Int?,
    star: Int,
): StarFill =
    when {
        halfStars == null || halfStars < star * 2 - 1 -> StarFill.Empty
        halfStars == star * 2 - 1 -> StarFill.Half
        else -> StarFill.Full
    }

/**
 * The rating (half stars) after tapping [star]: the whole star; on the current rating's star, a half star first when
 * [allowHalf], then nothing. Whole stars round a rating set elsewhere (7 = 3.5 shows as 4).
 */
internal fun starTap(
    halfStars: Int?,
    star: Int,
    allowHalf: Boolean,
): Int? {
    val whole = star * 2
    return when {
        !allowHalf -> if (wholeHalfStars(halfStars) == whole) null else whole
        halfStars == whole -> whole - 1
        halfStars == whole - 1 -> null
        else -> whole
    }
}

/** [halfStars] rounded to whole stars, still in half stars (7 -> 8). */
private fun wholeHalfStars(halfStars: Int?): Int? =
    halfStars?.let { (it / 2.0).roundToInt().coerceIn(1, MAX_STARS) * 2 }

private fun halfLabel(halfStars: Int) = "${halfStars / 2}.5"

private const val MAX_STARS = 5
