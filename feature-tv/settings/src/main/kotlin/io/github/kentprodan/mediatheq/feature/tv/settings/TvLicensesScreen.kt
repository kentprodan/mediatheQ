package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.LicenseEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem

/** The licenses list and one license's page share the parsed report: each entry builds its own (a few ms). */
@Composable
fun TvLicensesRoute(
    report: () -> String,
    notices: () -> String,
    onOpen: (String) -> Unit,
    viewModel: TvLicensesViewModel = viewModel { TvLicensesViewModel(report, notices) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvLicensesScreen(state, onOpen)
}

@Composable
fun TvLicenseRoute(
    id: String,
    report: () -> String,
    notices: () -> String,
    viewModel: TvLicensesViewModel = viewModel { TvLicensesViewModel(report, notices) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvLicenseScreen((state as? TvLicensesUiState.Content)?.entries?.firstOrNull { it.id == id })
}

/** The open-source licenses (#243): the app, the copied assets, then every library; select opens its license. */
@Composable
fun TvLicensesScreen(
    state: TvLicensesUiState,
    onOpen: (String) -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        val first = remember { FocusRequester() }
        LazyColumn(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start).width(720.dp),
            contentPadding = PaddingValues(48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Heading(stringResource(R.string.tv_licenses_title)) }
            when (state) {
                TvLicensesUiState.Loading -> {
                    item { Text(stringResource(R.string.tv_licenses_loading)) }
                }

                is TvLicensesUiState.Content -> {
                    items(state.entries, key = { it.id }) { entry ->
                        MqTvListItem(
                            entry.name,
                            { onOpen(entry.id) },
                            if (entry == state.entries.first()) Modifier.focusRequester(first) else Modifier,
                            supporting = listOfNotNull(entry.detail, entry.licenseName).joinToString(" · "),
                        )
                    }
                }
            }
        }
        if (state is TvLicensesUiState.Content) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/**
 * One license (#243): the entry, its source, its NOTICE and the license text, one focusable block per paragraph so
 * the remote's Down walks (and scrolls) through a long text. Back returns to the list.
 */
@Composable
fun TvLicenseScreen(entry: LicenseEntry?) {
    Surface(Modifier.fillMaxSize()) {
        if (entry == null) return@Surface
        val first = remember { FocusRequester() }
        val paragraphs =
            listOfNotNull(entry.url, entry.notice, entry.licenseText)
                .flatMap { it.split(PARAGRAPH) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        LazyColumn(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start).width(840.dp),
            contentPadding = PaddingValues(48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Heading(entry.name) }
            item {
                Text(
                    listOfNotNull(entry.detail, entry.licenseName).joinToString(" · "),
                    Modifier.padding(bottom = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(paragraphs.withIndex().toList(), key = { it.index }) { (index, text) ->
                // Focusable only to move through the text: select does nothing.
                Surface(
                    onClick = {},
                    modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun Heading(text: String) =
    Text(
        text,
        Modifier.padding(bottom = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )

private val PARAGRAPH = Regex("\n\\s*\n")
