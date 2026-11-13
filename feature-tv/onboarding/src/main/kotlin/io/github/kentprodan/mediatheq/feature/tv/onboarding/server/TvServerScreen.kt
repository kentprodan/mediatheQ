package io.github.kentprodan.mediatheq.feature.tv.onboarding.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvTextField
import io.github.kentprodan.mediatheq.feature.tv.onboarding.R
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingLayout

@Composable
internal fun TvServerRoute(
    onNavigate: (NavKey) -> Unit,
    viewModel: TvServerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.nextKey) {
        state.nextKey?.let {
            onNavigate(it)
            viewModel.onAction(TvServerAction.NavigationHandled)
        }
    }
    TvServerScreen(state, viewModel::onAction)
}

/** The server address (ADR 0040). Focus starts in the field; Connect checks the address. */
@Composable
fun TvServerScreen(
    state: TvServerUiState,
    onAction: (TvServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = remember { FocusRequester() }
    TvOnboardingLayout(stringResource(R.string.tv_onboarding_welcome), modifier) {
        if (state.notice != null) TvOnboardingError(stringResource(R.string.tv_onboarding_signed_out))
        Text(stringResource(R.string.tv_onboarding_server_intro), style = MaterialTheme.typography.bodyLarge)
        MqTvTextField(
            state.address,
            { onAction(TvServerAction.AddressChanged(it)) },
            stringResource(R.string.tv_onboarding_server_address),
            Modifier.focusRequester(field),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onDone = { onAction(TvServerAction.Connect) },
        )
        state.error?.let { TvOnboardingError(it.text()) }
        MqTvButton(
            stringResource(if (state.checking) R.string.tv_onboarding_checking else R.string.tv_onboarding_connect),
            onClick = { onAction(TvServerAction.Connect) },
            enabled = !state.checking && state.address.isNotBlank(),
        )
    }
    LaunchedEffect(Unit) { field.requestFocus() }
}

@Composable
private fun TvServerError.text(): String =
    when (this) {
        TvServerError.InvalidAddress -> stringResource(R.string.tv_onboarding_error_invalid)
        TvServerError.CleartextNotAllowed -> stringResource(R.string.tv_onboarding_error_cleartext)
        TvServerError.Unreachable -> stringResource(R.string.tv_onboarding_error_unreachable)
        TvServerError.NotJellyfin -> stringResource(R.string.tv_onboarding_error_not_jellyfin)
        is TvServerError.Unsupported -> stringResource(R.string.tv_onboarding_error_unsupported, version)
        TvServerError.Other -> stringResource(R.string.tv_onboarding_error_other)
    }
