package io.github.kentprodan.mediatheq.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.LibraryHubSpacing
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.ui.icon
import io.github.kentprodan.mediatheq.core.ui.label

@Composable
internal fun LibraryHubRoute(
    onOpen: (LibraryShortcut) -> Unit,
    viewModel: LibraryHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryHubScreen(state, { action -> if (action is LibraryHubAction.Open) onOpen(action.shortcut) })
}

/**
 * Stateless Library hub (#129): the Library icon and title, then a tile per chosen list, in a grid ([LibraryHubStyle.Grid],
 * columns from Settings, landscape its own) or one row per tile ([LibraryHubStyle.List]).
 */
@Composable
fun LibraryHubScreen(
    state: LibraryHubUiState,
    onAction: (LibraryHubAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MqIcons.Library, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(MediatheqSpacing.m))
                        Text(stringResource(R.string.library_title))
                    }
                },
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (state is LibraryHubUiState.Content) {
            Tiles(state.page, onAction, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Tiles(
    page: LibraryPagePreferences,
    onAction: (LibraryHubAction) -> Unit,
    modifier: Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val grid = page.style == LibraryHubStyle.Grid
    val chosen =
        when {
            !grid -> 1
            landscape -> page.landscapeColumns
            else -> page.columns
        }
    val gap = page.spacing.gap
    // A large font gets fewer, wider tiles than chosen, so the labels stay readable.
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fit =
            ((maxWidth - MediatheqSpacing.l * 2 + gap) / (MediatheqSizes.libraryTileMinWidth * fontScale + gap))
                .toInt()
        TileGrid(page, grid, chosen.coerceAtMost(fit.coerceAtLeast(1)), gap, onAction)
    }
}

@Composable
private fun TileGrid(
    page: LibraryPagePreferences,
    grid: Boolean,
    columns: Int,
    gap: Dp,
    onAction: (LibraryHubAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MediatheqSpacing.l).plusBottomBars(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(page.shortcuts, key = { it.name }) { shortcut ->
            Tile(shortcut, grid) { onAction(LibraryHubAction.Open(shortcut)) }
        }
    }
}

/** A tonal tile: icon over its label in a grid, icon then label in a row. */
@Composable
private fun Tile(
    shortcut: LibraryShortcut,
    grid: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val label = stringResource(shortcut.label)
        if (grid) {
            Column(
                Modifier.fillMaxWidth().padding(MediatheqSpacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) {
                Icon(shortcut.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                // The column cap keeps tiles wide enough that words never break; two lines for "Album artists".
                Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(MediatheqSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(shortcut.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(MediatheqSpacing.l))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val LibraryHubSpacing.gap: Dp
    get() =
        when (this) {
            LibraryHubSpacing.Compact -> MediatheqSpacing.s
            LibraryHubSpacing.Normal -> MediatheqSpacing.m
            LibraryHubSpacing.Large -> MediatheqSpacing.l
        }
