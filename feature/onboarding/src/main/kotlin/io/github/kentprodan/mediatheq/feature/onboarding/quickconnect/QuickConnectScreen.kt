package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingError
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingLayout
import io.github.kentprodan.mediatheq.feature.onboarding.R

@Composable
internal fun QuickConnectRoute(
    key: OnboardingQuickConnectKey,
    onCancel: () -> Unit,
    viewModel: QuickConnectViewModel =
        hiltViewModel<QuickConnectViewModel, QuickConnectViewModel.Factory>(creationCallback = { it.create(key) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuickConnectScreen(state, viewModel::onAction, onCancel)
}

@Composable
fun QuickConnectScreen(
    state: QuickConnectUiState,
    onAction: (QuickConnectAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingLayout(
        title = stringResource(R.string.onboarding_quick_connect_title),
        body = stringResource(R.string.onboarding_quick_connect_body, state.serverName),
        modifier = modifier,
    ) {
        when (state) {
            is QuickConnectUiState.Starting -> {
                MqLoadingIndicator(Modifier.fillMaxWidth())
            }

            is QuickConnectUiState.Waiting -> {
                // Read digit by digit, not as one large number.
                val description =
                    stringResource(
                        R.string.onboarding_quick_connect_code_description,
                        state.code.toList().joinToString(" "),
                    )
                Text(
                    state.code,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                )
                Text(
                    stringResource(R.string.onboarding_quick_connect_waiting),
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            is QuickConnectUiState.Expired -> {
                OnboardingError(stringResource(R.string.onboarding_quick_connect_expired))
                Button(onClick = { onAction(QuickConnectAction.Restart) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_quick_connect_new_code))
                }
            }

            is QuickConnectUiState.Error -> {
                val message =
                    if (state.unreachable) R.string.onboarding_error_unreachable else R.string.onboarding_error_other
                OnboardingError(stringResource(message))
                Button(onClick = { onAction(QuickConnectAction.Restart) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_quick_connect_new_code))
                }
            }
        }
        OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_cancel))
        }
    }
}
