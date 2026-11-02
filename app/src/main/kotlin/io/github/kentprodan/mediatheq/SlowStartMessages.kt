package io.github.kentprodan.mediatheq

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/** Shows a track slow to start (#122) once in [snackbar], then [onShown] clears it; like [PlaybackErrorMessages]. */
@Composable
internal fun SlowStartMessages(
    slowStarts: Flow<NowPlayingItem?>,
    onShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(slowStarts, snackbar, resources) {
        slowStarts.filterNotNull().collect { item ->
            currentOnShown()
            snackbar.showSnackbar(resources.getString(R.string.playback_slow_start, item.title))
        }
    }
}
