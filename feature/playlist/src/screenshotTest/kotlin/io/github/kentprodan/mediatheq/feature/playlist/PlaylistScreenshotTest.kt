package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorsOf
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData
import kotlin.time.Duration.Companion.seconds

private val playlist = PreviewData.playlist
private val items =
    List(5) { i ->
        val base = if (i == 2) PreviewData.longTrack else PreviewData.track
        PlaylistItem("e$i", i, base.copy(id = MediaId(base.id.serverId, "t$i"), artwork = PreviewData.artwork))
    }

/** A deep green image: the page's colors with dynamic colors on (previews load no images). */
private val green = coverColorsOf(IntArray(SEED_PIXELS) { 0xFF2E7D32.toInt() })

@Composable
private fun Playlist(
    state: PlaylistUiState,
    colors: Boolean = false,
) = MediatheqTheme { PlaylistScreen(state, onAction = {}, onBack = {}, imageColors = green.takeIf { colors }) }

private val content = PlaylistUiState.Content(playlist, items)

/** Drag handles on every row; at fontScale 2.0 (part of @MqPreviews) titles ellipsize. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistContentScreenshot() = Playlist(PlaylistUiState.Content(playlist, items, currentTrackId = items[1].track.id))

/** Two rows selected: checks instead of covers, no handles or menus, the floating toolbar at the bottom. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistSelectionScreenshot() = Playlist(PlaylistUiState.Content(playlist, items, selected = setOf("e0", "e3")))

/** Tracks just added and not read back yet: no handles. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistPendingScreenshot() =
    Playlist(
        PlaylistUiState.Content(
            playlist,
            items + PlaylistItem(PlaylistItem.PENDING_ENTRY_ID, 5, PreviewData.track),
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun PlaylistEmptyScreenshot() =
    Playlist(PlaylistUiState.Content(playlist.copy(trackCount = 0, artwork = null), emptyList()))

@PreviewTest
@MqPreviews
@Composable
fun PlaylistNotFoundScreenshot() = Playlist(PlaylistUiState.NotFound)

@PreviewTest
@MqPreviews
@Composable
fun PlaylistLoadingScreenshot() = Playlist(PlaylistUiState.Loading)

/** Played from here and all downloaded: Resume after Play, the downloaded mark before the counts. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistResumeScreenshot() =
    Playlist(content.copy(resume = ResumePoint(items[2].track.id, 30.seconds), isDownloaded = true))

/** "Image color": the image's color as a gradient behind a smaller image. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistImageColorScreenshot() =
    Playlist(content.copy(page = PlaylistPagePreferences(headerBackground = PlaylistHeaderBackground.ImageColor)), true)

/** No background, and every hideable button hidden (they are in More). */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistNoBackgroundScreenshot() =
    Playlist(
        content.copy(
            page =
                PlaylistPagePreferences(
                    headerBackground = PlaylistHeaderBackground.None,
                    hiddenButtons = PlaylistButton.entries.toSet(),
                ),
        ),
    )

/** Dynamic colors on: the whole page takes the playlist image's scheme. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistDynamicColorsScreenshot() =
    Playlist(content.copy(page = PlaylistPagePreferences(dynamicColors = true)), true)

private const val SEED_PIXELS = 64
