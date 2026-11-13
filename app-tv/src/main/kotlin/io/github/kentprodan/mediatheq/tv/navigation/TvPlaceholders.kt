package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.tv.R

/** The drawer's screens until their issues land (#237 Library, #244 Search, #240 Now playing, #243 Settings). */
fun EntryProviderScope<NavKey>.tvPlaceholderEntries() {
    TvDestination.entries.filter { it != TvDestination.Home }.forEach { destination ->
        entry(destination.key) { TvPlaceholder(stringResource(destination.label)) }
    }
}

/** A screen still to come: its name and a line saying so. Nothing to focus, so focus stays in the drawer. */
@Composable
fun TvPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.tv_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
