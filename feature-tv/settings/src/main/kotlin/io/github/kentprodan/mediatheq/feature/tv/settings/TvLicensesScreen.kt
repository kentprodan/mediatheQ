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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory

/** The licenses list and each license page read and parse the report themselves, off the main thread (a few ms). */
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
        // Back from a license focuses the entry that opened it (ADR 0041).
        val focus = rememberFocusMemory()
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
                            Modifier.focusMemory(focus, entry.id),
                            supporting = listOfNotNull(entry.detail, entry.licenseName).joinToString(" · "),
                        )
                    }
                }
            }
        }
        if (state is TvLicensesUiState.Content) {
            LaunchedEffect(Unit) {
                // The entries are lazy items, laid out after this effect starts.
                withFrameNanos {}
                focus.restore(state.entries.firstOrNull()?.id)
            }
        }
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
        val focus = rememberFocusMemory()
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
                    modifier = Modifier.focusMemory(focus, index.toString()).fillMaxWidth(),
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
        LaunchedEffect(Unit) {
            // The paragraphs are lazy items, laid out after this effect starts.
            withFrameNanos {}
            focus.restore("0")
        }
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
