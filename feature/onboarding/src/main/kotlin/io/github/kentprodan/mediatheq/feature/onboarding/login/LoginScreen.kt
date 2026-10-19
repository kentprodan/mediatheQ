package io.github.kentprodan.mediatheq.feature.onboarding.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingError
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingLayout
import io.github.kentprodan.mediatheq.feature.onboarding.R

@Composable
internal fun LoginRoute(
    key: OnboardingLoginKey,
    onNavigate: (OnboardingQuickConnectKey) -> Unit,
    viewModel: LoginViewModel =
        hiltViewModel<LoginViewModel, LoginViewModel.Factory>(creationCallback = { it.create(key) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.nextKey) {
        state.nextKey?.let {
            onNavigate(it)
            viewModel.onAction(LoginAction.NavigationHandled)
        }
    }
    LoginScreen(state, viewModel::onAction)
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OnboardingLayout(title = stringResource(R.string.onboarding_login_title, state.serverName), modifier = modifier) {
        OutlinedTextField(
            value = state.userName,
            onValueChange = { onAction(LoginAction.UserNameChanged(it)) },
            label = { Text(stringResource(R.string.onboarding_username)) },
            singleLine = true,
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = { onAction(LoginAction.PasswordChanged(it)) },
            label = { Text(stringResource(R.string.onboarding_password)) },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.error == LoginError.WrongCredentials,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(LoginAction.Submit) }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) MqIcons.VisibilityOff else MqIcons.Visibility,
                        contentDescription = stringResource(passwordToggleLabel(passwordVisible)),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        state.error?.let { OnboardingError(it.message()) }
        Button(
            onClick = { onAction(LoginAction.Submit) },
            enabled = state.userName.isNotBlank() && !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.submitting) R.string.onboarding_signing_in else R.string.onboarding_sign_in))
        }
        OutlinedButton(
            onClick = { onAction(LoginAction.OpenQuickConnect) },
            enabled = state.quickConnect == QuickConnectAvailability.Available && !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_use_quick_connect))
        }
        if (state.quickConnect == QuickConnectAvailability.Disabled) {
            Text(stringResource(R.string.onboarding_quick_connect_disabled))
        }
    }
}

@Composable
private fun LoginError.message(): String =
    stringResource(
        when (this) {
            LoginError.WrongCredentials -> R.string.onboarding_error_wrong_credentials
            LoginError.Unreachable -> R.string.onboarding_error_unreachable
            LoginError.Storage -> R.string.onboarding_error_storage
            LoginError.Other -> R.string.onboarding_error_other
        },
    )

private fun passwordToggleLabel(visible: Boolean) =
    if (visible) R.string.onboarding_hide_password else R.string.onboarding_show_password
