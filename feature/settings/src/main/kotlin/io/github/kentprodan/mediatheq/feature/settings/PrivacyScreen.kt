package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** The sections of the Privacy page: what the app does (ADR 0013, 0026, #150), in the user's words. */
private val sections =
    listOf(
        R.string.settings_privacy_tracking to R.string.settings_privacy_tracking_text,
        R.string.settings_privacy_data to R.string.settings_privacy_data_text,
        R.string.settings_privacy_sign_in to R.string.settings_privacy_sign_in_text,
        R.string.settings_privacy_connections to R.string.settings_privacy_connections_text,
        R.string.settings_privacy_other to R.string.settings_privacy_other_text,
    )

/** Settings -> About -> Privacy (#151): what stays on the phone and what is sent where. Stateless. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_privacy), onBack) {
        sections.forEach { (title, text) ->
            item(key = title) {
                Column(Modifier.padding(top = MediatheqSpacing.l)) {
                    Text(
                        stringResource(title),
                        Modifier.padding(bottom = MediatheqSpacing.s).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
