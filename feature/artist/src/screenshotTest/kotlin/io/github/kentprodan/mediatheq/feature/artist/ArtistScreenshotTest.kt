package io.github.kentprodan.mediatheq.feature.artist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.ArtistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorsOf
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val artist =
    PreviewData.artist.copy(
        artwork = PreviewData.artwork,
        backdrop = Artwork("artist-1", "backdrop-tag", PreviewData.artwork.blurHash, ImageType.Backdrop),
        overview =
            "A Romanian band from Bucharest that mixes folk songs with hip-hop and electronic beats. " +
                "Their records sample old village recordings and bring them to the dance floor.",
    )
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
        artist,
        albums = albums(3),
        appearsOn = albums(1).map { it.copy(id = MediaId(it.id.serverId, "comp"), title = "Best of 2020") },
        tracks = tracks,
        playedTracks = tracks,
        genres = listOf("Folk", "Hip-Hop"),
    )

/** A deep green image: what the page looks like with its colors (previews load no images). */
private val green = coverColorsOf(IntArray(SEED_PIXELS) { 0xFF2E7D32.toInt() })

@Composable
private fun Artist(
    state: ArtistUiState,
    colors: Boolean = false,
) = MediatheqTheme {
    ArtistScreen(state, onAction = {}, onOpenAlbum = {}, onBack = {}, imageColors = green.takeIf { colors })
}

private fun ArtistUiState.Content.with(page: ArtistPagePreferences) = copy(page = page)

/** The backdrop edge to edge under the bar, then albums, top tracks, appears on; fontScale 2.0 wraps the buttons. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistBackdropScreenshot() = Artist(content)

/** Dynamic colors on: the whole page takes the image's scheme. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistDynamicColorsScreenshot() = Artist(content, colors = true)

/** The artist image edge to edge; no backdrop. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistImageScreenshot() =
    Artist(content.copy(artist = artist.copy(backdrop = null)).with(ArtistPagePreferences(dynamicColors = false)))

/** "Image color": the image's color as a gradient behind the avatar, app colors elsewhere. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistImageColorScreenshot() =
    Artist(
        content.with(
            ArtistPagePreferences(headerBackground = ArtistHeaderBackground.ImageColor, dynamicColors = false),
        ),
        colors = true,
    )

/** No background and no picture: the person icon in the cookie shape; no top tracks when nothing was played. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistNoBackgroundScreenshot() =
    Artist(
        content
            .copy(artist = PreviewData.artist, playedTracks = emptyList())
            .with(ArtistPagePreferences(headerBackground = ArtistHeaderBackground.None)),
    )

/** About first, with the biography, genres, counts, favorite and stars; switched-off sections are gone. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistAboutFirstScreenshot() =
    Artist(
        content
            .copy(artist = artist.copy(userData = UserData(isFavorite = true, rating = 8.0)))
            .with(
                ArtistPagePreferences(
                    headerBackground = ArtistHeaderBackground.None,
                    sectionOrder =
                        listOf(
                            ArtistSection.About,
                            ArtistSection.Albums,
                            ArtistSection.TopTracks,
                            ArtistSection.AppearsOn,
                        ),
                    showTopTracks = false,
                ),
            ),
    )

/** Many albums in two columns. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistManyAlbumsScreenshot() =
    Artist(
        content.copy(albums = albums(12), playedTracks = emptyList()).with(
            ArtistPagePreferences(headerBackground = ArtistHeaderBackground.None, albumColumns = 2),
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun ArtistEmptyScreenshot() =
    Artist(
        content.copy(albums = emptyList(), appearsOn = emptyList(), tracks = emptyList(), playedTracks = emptyList()),
    )

@PreviewTest
@MqPreviews
@Composable
fun ArtistLoadingScreenshot() = Artist(ArtistUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun ArtistNotFoundScreenshot() = Artist(ArtistUiState.NotFound)

/** "Show all": every played track, most played first. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistTopTracksScreenshot() = MediatheqTheme { ArtistTopTracksScreen(content, onAction = {}, onBack = {}) }

private const val SEED_PIXELS = 64
