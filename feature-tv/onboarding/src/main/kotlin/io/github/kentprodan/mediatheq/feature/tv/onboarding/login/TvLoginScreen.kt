package io.github.kentprodan.mediatheq.feature.tv.onboarding.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvTextField
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.feature.tv.onboarding.R
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingLayout

@Composable
internal fun TvLoginRoute(
    key: OnboardingLoginKey,
    viewModel: TvLoginViewModel =
        hiltViewModel<TvLoginViewModel, TvLoginViewModel.Factory>(
            key = key.toString(),
            creationCallback = { it.create(key) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvLoginScreen(state, viewModel::onAction)
}

/** User name and password (ADR 0040); focus starts in the user name, a failure keeps both fields. */
@Composable
fun TvLoginScreen(
    state: TvLoginUiState,
    onAction: (TvLoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val userName = remember { FocusRequester() }
    TvOnboardingLayout(stringResource(R.string.tv_onboarding_quick_connect_title, state.serverName), modifier) {
        MqTvTextField(
            state.userName,
            { onAction(TvLoginAction.UserNameChanged(it)) },
            stringResource(R.string.tv_onboarding_user_name),
            Modifier.focusRequester(userName),
        )
        MqTvTextField(
            state.password,
            { onAction(TvLoginAction.PasswordChanged(it)) },
            stringResource(R.string.tv_onboarding_password),
            password = true,
            imeAction = ImeAction.Done,
            onDone = { onAction(TvLoginAction.Submit) },
        )
        state.error?.let { TvOnboardingError(it.text()) }
        MqTvButton(
            stringResource(if (state.submitting) R.string.tv_onboarding_signing_in else R.string.tv_onboarding_sign_in),
            onClick = { onAction(TvLoginAction.Submit) },
            enabled = !state.submitting && state.userName.isNotBlank(),
        )
    }
    LaunchedEffect(Unit) { userName.requestFocus() }
}

@Composable
private fun TvLoginError.text(): String =
    stringResource(
        when (this) {
            TvLoginError.WrongCredentials -> R.string.tv_onboarding_error_wrong_credentials
            TvLoginError.Unreachable -> R.string.tv_onboarding_error_unreachable
            TvLoginError.Storage -> R.string.tv_onboarding_error_storage
            TvLoginError.Other -> R.string.tv_onboarding_error_other
        },
    )
