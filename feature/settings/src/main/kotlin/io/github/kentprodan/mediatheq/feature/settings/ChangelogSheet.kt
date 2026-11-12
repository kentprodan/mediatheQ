package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Settings -> Changelog (#151) as a bottom sheet, over the hub or the About page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangelogSheet(
    changelog: () -> String,
    onDismiss: () -> Unit,
    viewModel: ChangelogViewModel = viewModel { ChangelogViewModel(changelog) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) { ChangelogContent(state, viewModel::onAction) }
}

/** The changelog, newest version first; a version's header opens or closes its sections. Stateless. */
@Composable
fun ChangelogContent(
    state: ChangelogUiState,
    onAction: (ChangelogAction) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = MediatheqSpacing.xl)) {
        item(key = "title") {
            Text(
                stringResource(R.string.settings_changelog),
                Modifier
                    .padding(
                        horizontal = MediatheqSpacing.l,
                        vertical = MediatheqSpacing.s,
                    ).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (state is ChangelogUiState.Content) {
            if (state.versions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.settings_changelog_empty),
                        Modifier.padding(horizontal = MediatheqSpacing.l),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.versions, key = { it.name }) { version ->
                VersionBlock(version, open = version.name in state.open) {
                    onAction(ChangelogAction.Toggle(version.name))
                }
            }
        }
    }
}

@Composable
private fun VersionBlock(
    version: ChangelogVersion,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        val state =
            stringResource(if (open) R.string.settings_changelog_expanded else R.string.settings_changelog_collapsed)
        ListItem(
            onClick = onToggle,
            // On the sheet's own color.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.semantics { stateDescription = state },
            supportingContent = version.date?.let { { Text(it) } },
            trailingContent = {
                Icon(
                    MqIcons.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                )
            },
        ) {
            Text(
                if (version.unreleased) stringResource(R.string.settings_changelog_unreleased) else version.name,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (open) {
            Column(Modifier.padding(horizontal = MediatheqSpacing.l)) {
                version.sections.forEach { section ->
                    Text(
                        section.title,
                        Modifier
                            .padding(
                                top = MediatheqSpacing.s,
                                bottom = MediatheqSpacing.xs,
                            ).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    section.items.forEach {
                        Text(
                            stringResource(R.string.settings_changelog_item, it),
                            Modifier.padding(bottom = MediatheqSpacing.xs),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
