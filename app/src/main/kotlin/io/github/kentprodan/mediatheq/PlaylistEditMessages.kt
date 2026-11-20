package io.github.kentprodan.mediatheq

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DroppedPlaylistEdits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/** Offline playlist edits the server refused (#417): one app-wide message, then [onShown] clears it. */
@Composable
internal fun PlaylistEditMessages(
    dropped: Flow<DroppedPlaylistEdits?>,
    onShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(dropped, snackbar, resources) {
        dropped.filterNotNull().collect { edits ->
            currentOnShown()
            val message =
                if (edits.error ==
                    MqError.Forbidden
                ) {
                    R.string.playlist_edits_forbidden
                } else {
                    R.string.playlist_edits_gone
                }
            snackbar.showSnackbar(resources.getString(message, edits.name))
        }
    }
}
