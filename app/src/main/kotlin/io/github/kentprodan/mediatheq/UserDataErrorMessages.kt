package io.github.kentprodan.mediatheq

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.common.MqError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Shows each favorite or rating the server refused once in [snackbar], then [onShown] clears it. Collected in one
 * effect: keying an effect on the error would cancel the snackbar the moment the error is cleared.
 */
@Composable
internal fun UserDataErrorMessages(
    errors: Flow<MqError?>,
    onShown: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val message = stringResource(R.string.user_data_save_failed)
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(errors, snackbar) {
        errors.filterNotNull().collect {
            currentOnShown()
            snackbar.showSnackbar(message)
        }
    }
}
