package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind

/**
 * A movie or an episode in a list of mixed results (#325): its poster, title and year, or for an episode its series
 * and number ("Show · S1:E2").
 */
@Composable
fun VideoRow(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val season = video.seasonNumber
    val episode = video.episodeNumber
    val show = video.seriesName
    val subtitle =
        when {
            video.kind != VideoKind.Episode -> {
                video.year?.toString()
            }

            season != null && episode != null && show != null -> {
                stringResource(
                    R.string.core_ui_episode_of,
                    show,
                    season,
                    episode,
                )
            }

            else -> {
                show
            }
        }
    CoverRow(video.title, video.artwork, MqIcons.Movie, onClick, modifier, subtitle = subtitle)
}

/** A series in a list of mixed results (#325): its poster, title and year. */
@Composable
fun SeriesRow(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverRow(series.title, series.artwork, MqIcons.Tv, onClick, modifier, subtitle = series.year?.toString())
}
