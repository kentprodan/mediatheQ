package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val playlist = PreviewData.playlist
private val items =
    List(5) { i ->
        val base = if (i == 2) PreviewData.longTrack else PreviewData.track
        PlaylistItem("e$i", i, base.copy(id = MediaId(base.id.serverId, "t$i"), artwork = PreviewData.artwork))
    }

@Composable
private fun Playlist(state: PlaylistUiState) = MediatheqTheme { PlaylistScreen(state, onAction = {}, onBack = {}) }

/** Drag handles on every row; at fontScale 2.0 (part of @MqPreviews) titles ellipsize. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistContentScreenshot() = Playlist(PlaylistUiState.Content(playlist, items, currentTrackId = items[1].track.id))

/** Two rows selected: checks instead of covers, no handles or menus, the floating toolbar at the bottom. */
@PreviewTest
@MqPreviews
@Composable
fun PlaylistSelectionScreenshot() = Playlist(PlaylistUiState.Content(playlist, items, selected = setOf(0, 3)))

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
