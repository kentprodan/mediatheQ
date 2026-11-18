package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.ui.tv.title

@Composable
fun TvHomeRowsRoute(viewModel: TvHomeRowsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { TvHomeRowsScreen(it, viewModel::onAction) }
}

/**
 * Settings -> Home rows on TV (#431): Select shows or hides a row, holding Select (or the menu key) moves it up or
 * down. Focus follows a moved row, as its key stays the same.
 */
@Composable
fun TvHomeRowsScreen(
    state: TvHomeRowsState,
    onAction: (TvHomeRowsAction) -> Unit,
) {
    val rows = state.rows
    var moving by remember { mutableStateOf<HomeSection?>(null) }
    val focus = rememberFocusMemory()
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start).width(720.dp),
            contentPadding = PaddingValues(48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tv_settings_home_rows),
                    Modifier.padding(bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(R.string.tv_settings_home_rows_hint),
                    Modifier.padding(bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rows, key = { it.section.name }) { row ->
                MqTvListItem(
                    stringResource(row.section.title),
                    { onAction(TvHomeRowsAction.Toggle(row.section)) },
                    Modifier
                        .focusMemory(focus, row.section.name)
                        .onPreviewKeyEvent { event ->
                            val isMenu = event.key == Key.Menu
                            if (isMenu && event.type == KeyEventType.KeyUp) moving = row.section
                            isMenu
                        },
                    supporting = stringResource(cardStyleLabel(row.cardStyle)),
                    onLongClick = { moving = row.section },
                    trailing = { Switch(checked = row.shown, onCheckedChange = null) },
                )
            }
            // Genre rows (#431): per account, after the video rows.
            if (state.genres.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tv_settings_genre_rows),
                        Modifier.padding(top = 24.dp, bottom = 4.dp, start = 12.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.genres, key = { "genre/${it.first}" }) { (genre, on) ->
                    MqTvListItem(
                        genre,
                        { onAction(TvHomeRowsAction.ToggleGenre(genre)) },
                        Modifier.focusMemory(focus, "genre/$genre"),
                        trailing = { Switch(checked = on, onCheckedChange = null) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        rows.firstOrNull()?.let { focus.restore(it.section.name) }
    }
    moving?.let { section ->
        val current = rows.firstOrNull { it.section == section }?.cardStyle
        // The card variants (#518) follow Move up / Move down; the chosen one carries a check.
        val styles =
            (listOf(null) + TvCardStyle.entries).map { style ->
                MqTvMenuEntry(
                    stringResource(cardStyleLabel(style)),
                    MqTvIcons.Watched.takeIf { style == current },
                ) { onAction(TvHomeRowsAction.SetCardStyle(section, style)) }
            }
        MqTvMenu(
            stringResource(section.title),
            listOf(
                MqTvMenuEntry(stringResource(R.string.tv_settings_move_up), null) {
                    onAction(TvHomeRowsAction.Move(section, up = true))
                },
                MqTvMenuEntry(stringResource(R.string.tv_settings_move_down), null) {
                    onAction(TvHomeRowsAction.Move(section, up = false))
                },
            ) + styles,
            onDismiss = { moving = null },
        )
    }
}

private fun cardStyleLabel(style: TvCardStyle?): Int =
    when (style) {
        null -> R.string.tv_settings_cards_default
        TvCardStyle.Standard -> R.string.tv_settings_cards_standard
        TvCardStyle.Classic -> R.string.tv_settings_cards_classic
        TvCardStyle.Compact -> R.string.tv_settings_cards_compact
        TvCardStyle.WideStandard -> R.string.tv_settings_cards_wide_standard
        TvCardStyle.WideClassic -> R.string.tv_settings_cards_wide_classic
    }
