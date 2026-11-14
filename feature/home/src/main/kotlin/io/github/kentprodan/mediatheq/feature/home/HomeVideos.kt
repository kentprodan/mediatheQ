package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.ui.MqPosterCard
import kotlin.time.Duration

/**
 * A video section of Home (#275): 16:9 stills with the progress line (Continue watching, Next up, new episodes) or
 * posters (new movies). Continue watching and Next up play at once; a new movie opens its page, a new episode its series.
 */
@Composable
internal fun VideoRow(
    section: HomeSection,
    items: HomeItems.Videos,
    navigation: HomeNavigation,
) {
    val plays = section == HomeSection.ContinueWatching || section == HomeSection.NextUp
    val open = { video: Video ->
        when {
            plays -> navigation.onPlayVideo(video.id, video.resumePosition())
            video.kind == VideoKind.Movie -> navigation.onOpenMovie(video.id)
            else -> video.seriesId?.let(navigation.onOpenSeries) ?: navigation.onPlayVideo(video.id, Duration.ZERO)
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        items(items.videos, key = { it.id.toString() }) { video ->
            if (items.wide) {
                WideVideoCard(video, { open(video) })
            } else {
                MqPosterCard(
                    video.title,
                    video.year?.toString(),
                    video.artwork,
                    onClick = { open(video) },
                    modifier = Modifier.width(MediatheqSizes.albumCardInRow),
                    watched = video.userData.played,
                )
            }
        }
    }
}

/** A 16:9 still (a movie's backdrop) with the progress line, title and "Series · S2 E4" or the year. */
@Composable
private fun WideVideoCard(
    video: Video,
    onClick: () -> Unit,
) {
    val progress =
        video.userData.playbackPosition
            .takeIf { !video.userData.played && it > Duration.ZERO && video.duration > Duration.ZERO }
            ?.let { (it / video.duration).toFloat().coerceIn(0f, 1f) }
    Column(Modifier.width(WideCardWidth).clickable(onClick = onClick)) {
        Box {
            MqCover(
                if (video.kind == VideoKind.Movie) video.backdrop ?: video.artwork else video.artwork,
                contentDescription = null,
                aspectRatio = 16 / 9f,
            )
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(MediatheqSpacing.s),
                )
            }
        }
        Text(
            video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MediatheqSpacing.s),
        )
        video.subtitle()?.let {
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

private fun Video.subtitle(): String? =
    when (kind) {
        VideoKind.Movie -> {
            year?.toString()
        }

        VideoKind.Episode -> {
            val number = listOfNotNull(seasonNumber?.let { "S$it" }, episodeNumber?.let { "E$it" }).joinToString(" ")
            listOfNotNull(seriesName, number.ifEmpty { null }).joinToString(" · ").ifEmpty { null }
        }
    }

private val WideCardWidth = 240.dp
