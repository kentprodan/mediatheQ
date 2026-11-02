package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Track

/** The trailing details of a track row as [LocalMediaListStyle] chooses them, first above second. */
@Composable
internal fun TrackDetails(track: Track) {
    val style = LocalMediaListStyle.current
    val shown = style.trackDetails.filter { it.isShown(track, style) }
    if (shown.isEmpty()) return
    Column(horizontalAlignment = Alignment.End) {
        shown.forEach { TrackDetailView(track, it, style) }
    }
}

private fun TrackDetail.isShown(
    track: Track,
    style: MediaListStyle,
): Boolean =
    when (this) {
        TrackDetail.Favorite -> track.userData.isFavorite
        TrackDetail.Offline -> track.id in style.downloaded
        TrackDetail.Rating -> style.halfStars != null && track.userData.halfStars != null
        TrackDetail.PlayCount -> true
        else -> trackDetailText(track, this) != null
    }

@Composable
private fun TrackDetailView(
    track: Track,
    detail: TrackDetail,
    style: MediaListStyle,
) {
    val small = MaterialTheme.typography.bodySmall
    when (detail) {
        TrackDetail.Favorite -> {
            DetailIcon(MqIcons.FavoriteFilled, stringResource(R.string.core_ui_favorite))
        }

        TrackDetail.Offline -> {
            DetailIcon(MqIcons.DownloadForOffline, stringResource(R.string.core_ui_downloaded))
        }

        TrackDetail.Rating -> {
            RatingStars(track.userData.halfStars ?: 0, wholeStars = style.halfStars == false)
        }

        TrackDetail.PlayCount -> {
            val plays = track.userData.playCount
            Text(pluralStringResource(R.plurals.core_ui_play_count, plays, plays), style = small)
        }

        else -> {
            trackDetailText(track, detail)?.let { Text(it, style = small) }
        }
    }
}

@Composable
private fun DetailIcon(
    icon: ImageVector,
    description: String,
) {
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier.size(MediatheqSizes.trackDetailIcon),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** Five small stars for [halfStars] (1-10); whole-star mode rounds a half star up, as the rating control shows it. */
@Composable
private fun RatingStars(
    halfStars: Int,
    wholeStars: Boolean,
) {
    val value = if (wholeStars) (halfStars + 1) / 2 * 2 else halfStars
    val description = stringResource(R.string.core_ui_rating, value / 2f)
    Row(Modifier.clearAndSetSemantics { contentDescription = description }) {
        (1..5).forEach { star ->
            val icon =
                when {
                    value >= star * 2 -> MqIcons.StarFilled
                    value == star * 2 - 1 -> MqIcons.StarHalf
                    else -> MqIcons.Star
                }
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(MediatheqSizes.trackDetailIcon),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Favorite and offline marks in a corner of a cover, as [LocalMediaListStyle] turns them on (#123): on a tonal pill so
 * they read on any artwork. [compact] for row-sized covers: smaller, tight to the bottom corner.
 */
@Composable
internal fun BoxScope.CoverOverlays(
    isFavorite: Boolean,
    isDownloaded: Boolean,
    compact: Boolean = false,
) {
    val style = LocalMediaListStyle.current
    val favorite = isFavorite && style.favoriteOverlay
    val offline = isDownloaded && style.offlineOverlay
    if (!favorite && !offline) return
    val inset = if (compact) COMPACT_INSET else MediatheqSpacing.xs
    val size = if (compact) MediatheqSizes.trackDetailIcon else MediatheqSizes.coverOverlayIcon
    Surface(
        Modifier.align(if (compact) Alignment.BottomEnd else Alignment.TopEnd).padding(inset),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(inset)) {
            if (favorite) OverlayIcon(MqIcons.FavoriteFilled, stringResource(R.string.core_ui_favorite), size)
            if (offline) OverlayIcon(MqIcons.DownloadForOffline, stringResource(R.string.core_ui_downloaded), size)
        }
    }
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    description: String,
    size: Dp,
) {
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

private val COMPACT_INSET = 2.dp
