package io.github.kentprodan.mediatheq.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard

/** Until the real screens (#234 on): the name and three focusable cards, to prove the theme and D-pad focus (#233). */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 58.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                stringResource(R.string.tv_placeholder_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(
                    R.string.tv_placeholder_home,
                    R.string.tv_placeholder_library,
                    R.string.tv_placeholder_settings,
                ).forEachIndexed { index, title ->
                    MqTvCard(
                        stringResource(title),
                        onClick = {},
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
                    }
                }
            }
        }
    }
    // A TV screen starts with focus on its first item (ADR 0037).
    LaunchedEffect(Unit) { first.requestFocus() }
}
