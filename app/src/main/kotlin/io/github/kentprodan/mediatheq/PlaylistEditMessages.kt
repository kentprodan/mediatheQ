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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Offline playlist edits (#417): one app-wide message per playlist the server refused ([dropped], then [onShown]
 * clears them), and one when an answer to a conflict could not reach the server ([answerFailed], then
 * [onAnswerFailureShown]).
 */
@Composable
internal fun PlaylistEditMessages(
    dropped: Flow<List<DroppedPlaylistEdits>>,
    onShown: () -> Unit,
    answerFailed: Flow<Boolean>,
    onAnswerFailureShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnAnswerFailureShown by rememberUpdatedState(onAnswerFailureShown)
    LaunchedEffect(dropped, answerFailed, snackbar, resources) {
        launch {
            answerFailed.filter { it }.collect {
                currentOnAnswerFailureShown()
                snackbar.showSnackbar(resources.getString(R.string.playlist_conflict_failed))
            }
        }
        dropped.filter { it.isNotEmpty() }.collect { all ->
            currentOnShown()
            all.forEach { edits ->
                val message =
                    when (edits.error) {
                        MqError.Forbidden -> R.string.playlist_edits_forbidden
                        else -> R.string.playlist_edits_gone
                    }
                snackbar.showSnackbar(resources.getString(message, edits.name))
            }
        }
    }
}
