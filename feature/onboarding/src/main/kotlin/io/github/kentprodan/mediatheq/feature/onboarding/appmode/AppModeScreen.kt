package io.github.kentprodan.mediatheq.feature.onboarding.appmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.ui.description
import io.github.kentprodan.mediatheq.core.ui.icon
import io.github.kentprodan.mediatheq.core.ui.label
import io.github.kentprodan.mediatheq.feature.onboarding.OnboardingLayout
import io.github.kentprodan.mediatheq.feature.onboarding.R

/** The app mode step; the shell shows it after the first sign-in until a mode is stored. */
@Composable
fun AppModeRoute(viewModel: AppModeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppModeScreen(state, viewModel::onAction)
}

/** Three large cards, Music, Video and Both, each with one sentence, then Continue. Stateless. */
@Composable
fun AppModeScreen(
    state: AppModeUiState,
    onAction: (AppModeAction) -> Unit,
) {
    OnboardingLayout(
        stringResource(R.string.onboarding_app_mode_title),
        body = stringResource(R.string.onboarding_app_mode_body),
    ) {
        AppMode.entries.forEach { mode ->
            val selected = mode == state.selected
            OutlinedCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected, role = Role.RadioButton) { onAction(AppModeAction.Select(mode)) },
                colors =
                    if (selected) {
                        androidx.compose.material3.CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        androidx.compose.material3.CardDefaults
                            .outlinedCardColors()
                    },
            ) {
                Row(
                    Modifier.padding(MediatheqSpacing.l),
                    horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(mode.icon, contentDescription = null, Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(mode.label), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(mode.description), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selected) Icon(MqIcons.Check, contentDescription = null)
                }
            }
        }
        Button(onClick = { onAction(AppModeAction.Continue) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_app_mode_continue))
        }
    }
}
