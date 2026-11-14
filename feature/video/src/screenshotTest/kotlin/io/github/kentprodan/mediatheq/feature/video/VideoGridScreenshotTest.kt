package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId

private fun posters(vararg titles: String) =
    titles.mapIndexed { i, title ->
        PosterItem(MediaId(ServerId("s"), "v$i"), title, (2010 + i).toString(), null, watched = i % 3 == 0, null)
    }

@Composable
private fun Grid(
    texts: VideoGridTexts,
    state: VideoGridUiState,
) = MediatheqTheme { VideoGridScreen(texts, state, {}, {}, {}) }

/** The Movies grid (#270): posters in 4 columns (album columns 3 + 1), watched ones checked. */
@PreviewTest
@MqPreviews
@Composable
fun MoviesGridScreenshot() =
    Grid(
        MovieTexts,
        VideoGridUiState.Content(
            posters("Arrival", "Blade Runner 2049", "Dune", "Her", "Interstellar", "Moon", "Sicario"),
            VideoSort.Name,
            unwatched = false,
            columns = 4,
        ),
    )

/** The Series grid with the Unwatched filter on: a badge counts each series' unwatched episodes. */
@PreviewTest
@MqPreviews
@Composable
fun SeriesGridScreenshot() =
    Grid(
        SeriesTexts,
        VideoGridUiState.Content(
            posters("Dark", "Severance", "The Expanse").map { it.copy(watched = false, unplayedCount = 7) },
            VideoSort.Year,
            unwatched = true,
            columns = 4,
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun MoviesEmptyScreenshot() = Grid(MovieTexts, VideoGridUiState.Content(emptyList(), VideoSort.Name, false, 4))

@PreviewTest
@MqPreviews
@Composable
fun SeriesAllWatchedScreenshot() = Grid(SeriesTexts, VideoGridUiState.Content(emptyList(), VideoSort.Name, true, 4))

@PreviewTest
@MqPreviews
@Composable
fun MoviesErrorScreenshot() = Grid(MovieTexts, VideoGridUiState.Error)
