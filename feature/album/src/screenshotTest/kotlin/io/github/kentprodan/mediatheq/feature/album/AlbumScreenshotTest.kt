package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val album = PreviewData.album.copy(artwork = PreviewData.artwork, year = 2024)
private val tracks =
    List(5) { i ->
        val base = if (i == 2) PreviewData.longTrack else PreviewData.track
        base.copy(id = MediaId(base.id.serverId, "t$i"), trackNumber = i + 1, discNumber = 1)
    }

@Composable
private fun Album(state: AlbumUiState) = MediatheqTheme { AlbumScreen(album.id, state, onAction = {}, onBack = {}) }

/** Single disc; at fontScale 2.0 (part of @MqPreviews) the header and buttons must not clip. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumSingleDiscScreenshot() = Album(AlbumUiState.Content(album, listOf(Disc(1, tracks))))

/** The second track is playing: equalizer icon instead of its number, primary title. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumPlayingTrackScreenshot() =
    Album(AlbumUiState.Content(album, listOf(Disc(1, tracks)), currentTrackId = tracks[1].id))

/** A favorite album rated with three stars. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumFavoriteRatedScreenshot() =
    Album(
        AlbumUiState.Content(
            album.copy(userData = UserData(isFavorite = true, rating = 6.0)),
            listOf(Disc(1, tracks)),
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun AlbumMultiDiscScreenshot() =
    Album(
        AlbumUiState.Content(
            album,
            listOf(
                Disc(1, tracks.take(2)),
                Disc(
                    2,
                    tracks.drop(2).map { it.copy(id = MediaId(it.id.serverId, "d2-${it.id.itemId}"), discNumber = 2) },
                ),
            ),
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun AlbumLongTitlesScreenshot() =
    Album(AlbumUiState.Content(PreviewData.longAlbum.copy(year = 1999), listOf(Disc(1, listOf(PreviewData.longTrack)))))

@PreviewTest
@MqPreviews
@Composable
fun AlbumLoadingScreenshot() = Album(AlbumUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun AlbumNotFoundScreenshot() = Album(AlbumUiState.NotFound)
