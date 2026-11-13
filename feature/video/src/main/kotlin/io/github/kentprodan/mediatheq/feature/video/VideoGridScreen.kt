package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.MqPosterCard

/** What differs between the Movies and the Series grid. */
data class VideoGridTexts(
    val title: Int,
    val emptyTitle: Int,
    val emptyBody: Int,
    val icon: @Composable () -> ImageVector,
)

/**
 * Stateless poster grid (#270): a top bar with Back and the sort menu, the Unwatched chip, then the posters. Nothing
 * synced (or nothing unwatched) shows an empty state; a failed read an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGridScreen(
    texts: VideoGridTexts,
    state: VideoGridUiState,
    onAction: (VideoGridAction) -> Unit,
    onOpen: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(texts.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.video_back))
                    }
                },
                actions = { if (state is VideoGridUiState.Content) SortMenu(state.sort, onAction) },
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                VideoGridUiState.Loading -> {
                    // A local read of a few milliseconds: nothing to show meanwhile.
                }

                VideoGridUiState.Error -> {
                    MqEmptyState(
                        texts.icon(),
                        stringResource(R.string.video_error_title),
                        body = stringResource(R.string.video_error_body),
                    )
                }

                is VideoGridUiState.Content -> {
                    Grid(texts, state, onAction, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Grid(
    texts: VideoGridTexts,
    state: VideoGridUiState.Content,
    onAction: (VideoGridAction) -> Unit,
    onOpen: (MediaId) -> Unit,
) {
    Column {
        FilterChip(
            selected = state.unwatched,
            onClick = { onAction(VideoGridAction.SetUnwatched(!state.unwatched)) },
            label = { Text(stringResource(R.string.video_unwatched)) },
            modifier = Modifier.padding(horizontal = MediatheqSpacing.l),
            leadingIcon =
                if (state.unwatched) {
                    { Icon(MqIcons.Check, contentDescription = null) }
                } else {
                    null
                },
        )
        if (state.items.isEmpty()) {
            if (state.unwatched) {
                MqEmptyState(
                    texts.icon(),
                    stringResource(R.string.video_all_watched_title),
                    body = stringResource(R.string.video_all_watched_body),
                )
            } else {
                MqEmptyState(texts.icon(), stringResource(texts.emptyTitle), body = stringResource(texts.emptyBody))
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MediatheqSpacing.l).plusBottomBars(),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        ) {
            items(state.items, key = { it.id.toString() }) { item ->
                MqPosterCard(
                    item.title,
                    item.subtitle,
                    item.artwork,
                    onClick = { onOpen(item.id) },
                    watched = item.watched,
                    unplayedCount = item.unplayedCount,
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    sort: VideoSort,
    onAction: (VideoGridAction) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(MqIcons.Sort, contentDescription = stringResource(R.string.video_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            VideoSort.entries.forEach { option ->
                val checked = option == sort
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        onAction(VideoGridAction.SetSort(option))
                        open = false
                    },
                    leadingIcon = { if (checked) Icon(MqIcons.Check, contentDescription = null) else Box(Modifier) },
                    // TalkBack announces which option is active.
                    modifier = Modifier.semantics { selected = checked },
                )
            }
        }
    }
}

private val VideoSort.label: Int
    get() =
        when (this) {
            VideoSort.Name -> R.string.video_sort_name
            VideoSort.DateAdded -> R.string.video_sort_added
            VideoSort.Year -> R.string.video_sort_year
        }
