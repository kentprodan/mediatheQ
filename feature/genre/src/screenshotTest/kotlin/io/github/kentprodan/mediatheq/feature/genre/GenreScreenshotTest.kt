package io.github.kentprodan.mediatheq.feature.genre

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.GenreHeaderBackground
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorsOf
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val albums =
    List(4) { i ->
        val base = if (i % 2 == 0) PreviewData.album else PreviewData.longAlbum
        base.copy(id = MediaId(base.id.serverId, "album-$i"), artwork = PreviewData.artwork.takeIf { i != 2 })
    }
private val tracks =
    List(12) { i ->
        val base = if (i % 2 == 0) PreviewData.track else PreviewData.longTrack
        base.copy(
            id = MediaId(base.id.serverId, "t-$i"),
            artwork = PreviewData.artwork,
            userData = UserData(playCount = 12 - i),
        )
    }
private val genre = Genre(MediaId(PreviewData.album.id.serverId, "g"), "Electrónica", albumCount = 4, trackCount = 12)

/** A credit-only artist: no picture, the person placeholder. */
private val guest = Artist(MediaId(PreviewData.album.id.serverId, "guest"), "Guest Vocalist")

private val content =
    GenreUiState.Content(
        genre,
        albums,
        albumArtists = listOf(PreviewData.artist),
        tracks = tracks,
        trackArtists = listOf(PreviewData.artist, guest, PreviewData.longTrack.artists[1]),
        page = GenrePagePreferences(topTracksCount = 5),
    )

/** A deep green image: the page's colors with dynamic colors on (previews load no images). */
private val green = coverColorsOf(IntArray(SEED_PIXELS) { 0xFF2E7D32.toInt() })

@Composable
private fun Page(
    state: GenreUiState,
    colors: Boolean = false,
) = MediatheqTheme { GenreScreen(state, {}, {}, {}, {}, imageColors = green.takeIf { colors }) }

private fun GenreUiState.Content.with(page: GenrePagePreferences) = copy(page = page)

/** The most played album's cover edge to edge, then artists, albums and top tracks with Show all. */
@PreviewTest
@MqPreviews
@Composable
fun GenrePageScreenshot() = Page(content)

/** Dynamic colors on: the whole page takes the header image's scheme. */
@PreviewTest
@MqPreviews
@Composable
fun GenreDynamicColorsScreenshot() = Page(content.with(content.page.copy(dynamicColors = true)), colors = true)

/** "Image color": the image's color as a gradient behind a smaller cover. */
@PreviewTest
@MqPreviews
@Composable
fun GenreImageColorScreenshot() =
    Page(content.with(content.page.copy(headerBackground = GenreHeaderBackground.ImageColor)), colors = true)

/** No background; top tracks first, album artists on, artists off. */
@PreviewTest
@MqPreviews
@Composable
fun GenreSectionsScreenshot() =
    Page(
        content.with(
            content.page.copy(
                headerBackground = GenreHeaderBackground.None,
                sectionOrder =
                    listOf(
                        GenreSection.TopTracks,
                        GenreSection.AlbumArtists,
                        GenreSection.Albums,
                        GenreSection.Artists,
                    ),
                showAlbumArtists = true,
                showArtists = false,
            ),
        ),
    )

/** "Show all": every track, most played first. */
@PreviewTest
@MqPreviews
@Composable
fun GenreTopTracksScreenshot() = MediatheqTheme { GenreTopTracksScreen(content, {}, {}) }

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

private const val SEED_PIXELS = 64
