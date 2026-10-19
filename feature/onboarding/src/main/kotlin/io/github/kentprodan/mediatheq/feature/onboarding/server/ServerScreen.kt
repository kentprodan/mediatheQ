package io.github.kentprodan.mediatheq.feature.onboarding.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingError
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingLayout
import io.github.kentprodan.mediatheq.feature.onboarding.R

@Composable
internal fun ServerRoute(
    onNavigate: (OnboardingLoginKey) -> Unit,
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.nextKey) {
        state.nextKey?.let {
            onNavigate(it)
            viewModel.onAction(ServerAction.NavigationHandled)
        }
    }
    ServerScreen(state, viewModel::onAction)
}

@Composable
fun ServerScreen(
    state: ServerUiState,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingLayout(
        title = stringResource(R.string.onboarding_welcome),
        body = stringResource(R.string.onboarding_server_body),
        modifier = modifier,
    ) {
        state.notice?.let { OnboardingError(it.message()) }
        OutlinedTextField(
            value = state.address,
            onValueChange = { onAction(ServerAction.AddressChanged(it)) },
            label = { Text(stringResource(R.string.onboarding_server_address)) },
            placeholder = { Text(stringResource(R.string.onboarding_server_hint)) },
            singleLine = true,
            isError = state.error != null,
            enabled = !state.checking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAction(ServerAction.Connect) }),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { OnboardingError(it.message()) }
        Button(
            onClick = { onAction(ServerAction.Connect) },
            enabled = state.address.isNotBlank() && !state.checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.checking) R.string.onboarding_connecting else R.string.onboarding_connect))
        }
    }
}

@Composable
private fun ServerError.message(): String =
    when (this) {
        ServerError.InvalidAddress -> stringResource(R.string.onboarding_error_invalid_address)
        ServerError.CleartextNotAllowed -> stringResource(R.string.onboarding_error_cleartext)
        ServerError.Unreachable -> stringResource(R.string.onboarding_error_unreachable)
        ServerError.NotJellyfin -> stringResource(R.string.onboarding_error_not_jellyfin)
        is ServerError.Unsupported -> stringResource(R.string.onboarding_error_unsupported, version)
        ServerError.Other -> stringResource(R.string.onboarding_error_other)
    }

@Composable
private fun LogoutReason.message(): String =
    when (this) {
        LogoutReason.SessionExpired -> stringResource(R.string.onboarding_notice_session_expired)
        LogoutReason.StorageError -> stringResource(R.string.onboarding_notice_storage)
    }
