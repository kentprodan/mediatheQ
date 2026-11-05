package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The Shortcuts section (#128): Track mix, Album mix and Decade mix as filled tonal cards in three columns. Decade mix
 * opens the decade picker; it is left out when no album has a year.
 */
@Composable
internal fun MixShortcuts(
    shortcuts: HomeItems.Shortcuts,
    onAction: (HomeAction) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        ShortcutCard(
            MqIcons.Shuffle,
            stringResource(R.string.home_track_mix),
        ) { onAction(HomeAction.PlayMix(Mix.Tracks)) }
        ShortcutCard(
            MqIcons.Library,
            stringResource(R.string.home_album_mix),
        ) { onAction(HomeAction.PlayMix(Mix.Albums)) }
        if (shortcuts.decades.isNotEmpty()) {
            ShortcutCard(MqIcons.Radio, stringResource(R.string.home_decade_mix)) { picking = true }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
    if (picking) {
        DecadeSheet(shortcuts.decades, onDismiss = { picking = false }) {
            picking = false
            onAction(HomeAction.PlayMix(Mix.Decade(it)))
        }
    }
}

@Composable
private fun RowScope.ShortcutCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MediatheqSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecadeSheet(
    decades: List<Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DecadeList(decades, onPick)
    }
}

/** The decade picker's content: a title and one row per decade of the library, oldest first. */
@Composable
internal fun DecadeList(
    decades: List<Int>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            stringResource(R.string.home_decade_mix),
            Modifier.padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
            items(decades, key = { it }) { decade ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_decade, decade)) },
                    modifier = Modifier.fillMaxWidth().clickable { onPick(decade) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
    }
}
