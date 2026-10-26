package io.github.kentprodan.mediatheq

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import io.github.kentprodan.mediatheq.core.playback.PlaybackError.Reason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Shows each track that could not play once in [snackbar] (#61), then [onShown] clears it. Collected in one effect,
 * like [UserDataErrorMessages].
 */
@Composable
internal fun PlaybackErrorMessages(
    errors: Flow<PlaybackError?>,
    onShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(errors, snackbar, resources) {
        errors.filterNotNull().collect { error ->
            currentOnShown()
            val message =
                when (error.reason to error.skipped) {
                    Reason.NotDownloaded to true -> R.string.playback_not_downloaded_skipped
                    Reason.NotDownloaded to false -> R.string.playback_not_downloaded
                    Reason.Failed to true -> R.string.playback_failed_skipped
                    else -> R.string.playback_failed
                }
            snackbar.showSnackbar(resources.getString(message, error.title))
        }
    }
}
