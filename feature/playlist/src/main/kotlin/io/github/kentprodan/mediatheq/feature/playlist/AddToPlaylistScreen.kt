package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.PlaylistRow

@Composable
internal fun AddToPlaylistRoute(
    trackIds: List<MediaId>,
    onClose: () -> Unit,
    viewModel: AddToPlaylistViewModel =
        hiltViewModel<AddToPlaylistViewModel, AddToPlaylistViewModel.Factory>(
            key = trackIds.joinToString(),
            creationCallback = { it.create(trackIds) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == AddToPlaylistUiState.Done) onClose() }
    AddToPlaylistScreen(state, viewModel::onAction, onClose)
}

/**
 * Stateless content of the "Add to playlist" dialog (the dialog window comes from the navigation scene): pick a
 * playlist, or name a new one.
 */
@Composable
fun AddToPlaylistScreen(
    state: AddToPlaylistUiState,
    onAction: (AddToPlaylistAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? AddToPlaylistUiState.Content ?: return
    val cancel = stringResource(R.string.playlist_cancel)
    if (content.creating) {
        val name = rememberTextFieldState()
        MqDialogContent(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { onAction(AddToPlaylistAction.Create(name.text.toString())) },
            dismissLabel = cancel,
            onDismiss = onDismiss,
            confirmEnabled = name.text.isNotBlank() && !content.busy,
            modifier = modifier,
        ) {
            Column {
                OutlinedTextField(
                    name,
                    label = { Text(stringResource(R.string.playlist_name)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                ErrorText(content)
            }
        }
    } else {
        MqDialogContent(
            title = stringResource(R.string.playlist_add_to),
            confirmLabel = stringResource(R.string.playlist_new),
            onConfirm = { onAction(AddToPlaylistAction.StartCreating) },
            dismissLabel = cancel,
            onDismiss = onDismiss,
            confirmEnabled = !content.busy,
            modifier = modifier,
        ) {
            Column {
                ErrorText(content)
                if (content.playlists.isEmpty()) Text(stringResource(R.string.playlist_none_yet))
                LazyColumn {
                    items(content.playlists, key = { it.id.toString() }) { playlist ->
                        PlaylistRow(playlist, onClick = { onAction(AddToPlaylistAction.Pick(playlist.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(content: AddToPlaylistUiState.Content) {
    if (content.error != null) {
        Text(
            stringResource(R.string.playlist_add_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
