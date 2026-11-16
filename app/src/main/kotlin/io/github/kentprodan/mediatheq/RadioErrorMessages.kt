package io.github.kentprodan.mediatheq

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import io.github.kentprodan.mediatheq.core.common.MqError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/** Start radio did not start (#379): one app-wide message, then [onShown] clears it. */
@Composable
internal fun RadioErrorMessages(
    errors: Flow<MqError?>,
    onShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(errors, snackbar, resources) {
        errors.filterNotNull().collect { error ->
            currentOnShown()
            val message =
                when (error) {
                    MqError.NotFound -> R.string.radio_nothing_in_library
                    MqError.Network -> R.string.radio_no_server
                    else -> R.string.radio_failed
                }
            snackbar.showSnackbar(resources.getString(message))
        }
    }
}
