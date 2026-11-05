package io.github.kentprodan.mediatheq.feature.genre

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val albums =
    List(4) { i ->
        val base = if (i % 2 == 0) PreviewData.album else PreviewData.longAlbum
        base.copy(id = MediaId(base.id.serverId, "album-$i"), artwork = PreviewData.artwork.takeIf { i != 2 })
    }
private val tracks =
    List(12) { i ->
        val base = if (i % 2 == 0) PreviewData.track else PreviewData.longTrack
        base.copy(id = MediaId(base.id.serverId, "t-$i"), artwork = PreviewData.artwork)
    }
private val genre = Genre(MediaId(PreviewData.album.id.serverId, "g"), "Electrónica", albumCount = 4, trackCount = 12)

@Composable
private fun Page(state: GenreUiState) = MediatheqTheme { GenreScreen(state, {}, {}, {}, {}) }

/** A genre page (#130): counts, Play / Shuffle, albums, album artists, the first ten tracks and Show all. */
@PreviewTest
@MqPreviews
@Composable
fun GenrePageScreenshot() =
    Page(GenreUiState.Content(genre, albums, listOf(PreviewData.artist, PreviewData.longTrack.artists[1]), tracks))

/** Before the first read: the top bar only. */
@PreviewTest
@MqPreviews
@Composable
fun GenreLoadingScreenshot() = Page(GenreUiState.Loading)

/** A genre nothing carries any more. */
@PreviewTest
@MqPreviews
@Composable
fun GenreNotFoundScreenshot() = Page(GenreUiState.NotFound)
