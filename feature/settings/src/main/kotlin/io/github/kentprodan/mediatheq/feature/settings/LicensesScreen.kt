package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

@Composable
internal fun LicensesRoute(
    report: () -> String,
    onBack: () -> Unit,
    viewModel: LicensesViewModel = viewModel { LicensesViewModel(report) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LicensesScreen(state, viewModel::onAction, onBack)
}

/** Settings -> About -> Open-source licenses: the app, copied assets and libraries; a row opens its license text. */
@Composable
fun LicensesScreen(
    state: LicensesUiState,
    onAction: (LicensesAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.licenses_close))
                    }
                },
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // A local read of a few milliseconds: nothing to show meanwhile.
        val content = state as? LicensesUiState.Content ?: return@Scaffold
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = MediatheqSpacing.l),
        ) {
            items(content.entries, key = { it.id }) { entry ->
                LicenseRow(
                    entry,
                    open = entry.id == content.openId,
                    onToggle = { onAction(LicensesAction.Toggle(entry.id)) },
                )
            }
        }
    }
}

@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        ListItem(
            onClick = onToggle,
            supportingContent = { Text(listOfNotNull(entry.detail, entry.licenseName).joinToString(" · ")) },
            trailingContent = {
                Icon(
                    MqIcons.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                )
            },
        ) { Text(entry.name) }
        if (open) {
            Column(Modifier.padding(horizontal = MediatheqSpacing.l)) {
                entry.licenseText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.url?.let { url ->
                    val uriHandler = LocalUriHandler.current
                    TextButton(
                        onClick = { uriHandler.openUri(url) },
                    ) { Text(stringResource(R.string.licenses_open_page)) }
                }
            }
        }
    }
}
