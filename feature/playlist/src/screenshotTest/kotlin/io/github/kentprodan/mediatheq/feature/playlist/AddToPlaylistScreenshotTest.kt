package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val playlists =
    listOf(
        PreviewData.playlist,
        PreviewData.playlist.copy(id = MediaId(PreviewData.playlist.id.serverId, "p2"), name = "Seară", artwork = null),
    )

// The dialog's content in place: dialog windows render blank in screenshot tests.
@Composable
private fun AddToPlaylist(state: AddToPlaylistUiState) =
    MediatheqTheme { AddToPlaylistScreen(state, onAction = {}, onDismiss = {}) }

@PreviewTest
@MqPreviews
@Composable
fun AddToPlaylistPickScreenshot() = AddToPlaylist(AddToPlaylistUiState.Content(playlists))

@PreviewTest
@MqPreviews
@Composable
fun AddToPlaylistErrorScreenshot() = AddToPlaylist(AddToPlaylistUiState.Content(playlists, error = MqError.Network))

@PreviewTest
@MqPreviews
@Composable
fun AddToPlaylistNoPlaylistsScreenshot() = AddToPlaylist(AddToPlaylistUiState.Content(emptyList()))

@PreviewTest
@MqPreviews
@Composable
fun AddToPlaylistCreateScreenshot() = AddToPlaylist(AddToPlaylistUiState.Content(playlists, creating = true))
