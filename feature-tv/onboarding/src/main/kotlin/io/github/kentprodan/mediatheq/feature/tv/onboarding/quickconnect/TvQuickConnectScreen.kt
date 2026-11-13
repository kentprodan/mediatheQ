package io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.feature.tv.onboarding.R
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.TvOnboardingLayout

@Composable
internal fun TvQuickConnectRoute(
    key: OnboardingQuickConnectKey,
    onUsePassword: () -> Unit,
    viewModel: TvQuickConnectViewModel =
        hiltViewModel<TvQuickConnectViewModel, TvQuickConnectViewModel.Factory>(
            key = key.toString(),
            creationCallback = { it.create(key) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvQuickConnectScreen(state, viewModel::onAction, onUsePassword)
}

/**
 * Quick Connect, the TV's first way in (ADR 0040): the code in large type while it waits. "Use password instead" is
 * always there; when Quick Connect is off it is the only way and takes the focus.
 */
@Composable
fun TvQuickConnectScreen(
    state: TvQuickConnectUiState,
    onAction: (TvQuickConnectAction) -> Unit,
    onUsePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val main = remember { FocusRequester() }
    val password = remember { FocusRequester() }
    val canRestart =
        state is TvQuickConnectUiState.Expired || state is TvQuickConnectUiState.Error ||
            state is TvQuickConnectUiState.Waiting
    TvOnboardingLayout(stringResource(R.string.tv_onboarding_quick_connect_title, state.serverName), modifier) {
        when (state) {
            is TvQuickConnectUiState.Starting -> {
                Text(
                    stringResource(R.string.tv_onboarding_quick_connect_starting),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is TvQuickConnectUiState.Waiting -> {
                Text(
                    stringResource(R.string.tv_onboarding_quick_connect_intro),
                    style = MaterialTheme.typography.bodyLarge,
                )
                // Read out digit by digit, so a screen reader does not say "one hundred twenty-three thousand".
                Text(
                    state.code,
                    Modifier.semantics { contentDescription = state.code.toList().joinToString(" ") },
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.tv_onboarding_quick_connect_waiting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is TvQuickConnectUiState.Disabled -> {
                Text(
                    stringResource(R.string.tv_onboarding_quick_connect_disabled),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is TvQuickConnectUiState.Expired -> {
                TvOnboardingError(stringResource(R.string.tv_onboarding_quick_connect_expired))
            }

            is TvQuickConnectUiState.Error -> {
                TvOnboardingError(
                    stringResource(
                        if (state.unreachable) {
                            R.string.tv_onboarding_quick_connect_unreachable
                        } else {
                            R.string.tv_onboarding_quick_connect_failed
                        },
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (canRestart) {
                MqTvButton(
                    stringResource(R.string.tv_onboarding_new_code),
                    onClick = { onAction(TvQuickConnectAction.Restart) },
                    modifier = Modifier.focusRequester(main),
                )
            }
            MqTvButton(
                stringResource(R.string.tv_onboarding_use_password),
                onClick = onUsePassword,
                modifier = Modifier.focusRequester(password),
            )
        }
    }
    // The first button of the screen; with Quick Connect off, the password.
    LaunchedEffect(canRestart) { if (canRestart) main.requestFocus() else password.requestFocus() }
}
