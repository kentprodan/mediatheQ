package io.github.kentprodan.mediatheq.feature.artist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val artist = PreviewData.artist
private val tracks =
    List(4) { i ->
        PreviewData.track.copy(
            id = MediaId(PreviewData.track.id.serverId, "t$i"),
            userData = UserData(playCount = 10 - i),
        )
    }

private fun albums(count: Int) =
    List(count) { i ->
        PreviewData.album.copy(
            id = MediaId(PreviewData.album.id.serverId, "al$i"),
            year = 2024 - i,
            artwork = PreviewData.artwork,
        )
    }

private val content =
    ArtistUiState.Content(
        artist.copy(artwork = PreviewData.artwork),
        albums = albums(3),
        appearsOn = albums(1).map { it.copy(id = MediaId(it.id.serverId, "comp"), title = "Best of 2020") },
        tracks = tracks,
        topTracks = tracks,
    )

@Composable
private fun Artist(state: ArtistUiState) =
    MediatheqTheme { ArtistScreen(state, onAction = {}, onOpenAlbum = {}, onBack = {}) }

/** Picture, top tracks, albums and appears-on; at fontScale 2.0 the header and buttons must not clip. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistWithImageScreenshot() = Artist(content)

/** No picture: the person icon in the cookie shape; no top tracks when nothing was played. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistWithoutImageScreenshot() = Artist(content.copy(artist = artist, topTracks = emptyList()))

/** Many albums: the grid starts right under the buttons. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistManyAlbumsScreenshot() = Artist(content.copy(albums = albums(12), topTracks = emptyList()))

@PreviewTest
@MqPreviews
@Composable
fun ArtistEmptyScreenshot() =
    Artist(content.copy(albums = emptyList(), appearsOn = emptyList(), tracks = emptyList(), topTracks = emptyList()))

@PreviewTest
@MqPreviews
@Composable
fun ArtistLoadingScreenshot() = Artist(ArtistUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun ArtistNotFoundScreenshot() = Artist(ArtistUiState.NotFound)

/** A favorite artist rated with five stars. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistFavoriteRatedScreenshot() =
    Artist(content.copy(artist = content.artist.copy(userData = UserData(isFavorite = true, rating = 10.0))))
