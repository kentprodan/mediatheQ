package io.github.kentprodan.mediatheq.feature.tv.artist

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.FocusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvBackdrop
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvCover
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvRowHeader
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvTrackRow
import kotlinx.coroutines.launch

@Composable
fun TvArtistRoute(
    artistId: MediaId,
    onOpenAlbum: (MediaId) -> Unit,
) {
    val viewModel =
        hiltViewModel<TvArtistViewModel, TvArtistViewModel.Factory>(key = artistId.toString()) { it.create(artistId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvArtistScreen(state, viewModel::onAction, onOpenAlbum)
}

/**
 * The artist page on TV (#239): the backdrop behind the picture, name and biography (select expands it, Up/Down
 * scroll a long one), Play all / Shuffle all, then rows of their albums and the albums they appear on, and the top
 * tracks. Focus starts on Play all.
 */
@Composable
fun TvArtistScreen(
    state: TvArtistUiState,
    onAction: (TvArtistAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TvArtistUiState.Loading -> {
            Box(modifier.fillMaxSize())
        }

        TvArtistUiState.NotFound -> {
            MqTvMessage(
                stringResource(R.string.tv_artist_not_found_title),
                stringResource(R.string.tv_artist_not_found_body),
                modifier,
            )
        }

        is TvArtistUiState.Content -> {
            Content(state, onAction, onOpenAlbum, modifier)
        }
    }
}

@Composable
private fun Content(
    state: TvArtistUiState.Content,
    onAction: (TvArtistAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    modifier: Modifier,
) {
    // Back from an album: the card that opened it; the first time Play all (the biography when nothing plays).
    val focus = rememberFocusMemory()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") { Header(state, onAction, focus) }
        if (state.isEmpty) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.tv_artist_empty),
                    Modifier.padding(horizontal = EDGE),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (state.albums.isNotEmpty()) {
            item(
                key = "albums",
            ) { AlbumRow(stringResource(R.string.tv_artist_albums), "albums", state.albums, focus, onOpenAlbum) }
        }
        if (state.appearsOn.isNotEmpty()) {
            item(key = "appears") {
                AlbumRow(stringResource(R.string.tv_artist_appears_on), "appears", state.appearsOn, focus, onOpenAlbum)
            }
        }
        if (state.topTracks.isNotEmpty()) {
            item(
                key = "top",
            ) { MqTvRowHeader(stringResource(R.string.tv_artist_top_tracks), Modifier.padding(horizontal = EDGE)) }
            itemsIndexed(state.topTracks, key = { _, track -> "top ${track.id}" }) { index, track ->
                MqTvTrackRow(
                    track,
                    onClick = { onAction(TvArtistAction.PlayTopTrack(index)) },
                    Modifier.padding(horizontal = EDGE).focusMemory(focus, "top/${track.id}"),
                    number = index + 1,
                    isPlaying = track.id == state.currentTrackId,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        // The header is a lazy item, laid out after this effect starts.
        withFrameNanos {}
        focus.restore(if (state.tracks.isNotEmpty()) PLAY_ALL else BIOGRAPHY)
    }
}

@Composable
private fun Header(
    state: TvArtistUiState.Content,
    onAction: (TvArtistAction) -> Unit,
    focus: FocusMemory,
) {
    val artist = state.artist
    MqTvBackdrop(artist.backdrop, Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = EDGE, end = EDGE, top = 40.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MqTvCover(artist.artwork, MqTvIcons.Person, Modifier.size(PICTURE).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    artist.name,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                artist.overview?.takeIf { it.isNotBlank() }?.let {
                    Biography(
                        it,
                        Modifier.focusMemory(focus, BIOGRAPHY),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val canPlay = state.tracks.isNotEmpty()
                    MqTvButton(
                        stringResource(R.string.tv_artist_play_all),
                        { onAction(TvArtistAction.PlayAll) },
                        Modifier.focusMemory(focus, PLAY_ALL),
                        enabled = canPlay,
                        icon = MqTvIcons.Play,
                    )
                    MqTvButton(
                        stringResource(R.string.tv_artist_shuffle_all),
                        { onAction(TvArtistAction.ShuffleAll) },
                        enabled = canPlay,
                        icon = MqTvIcons.Shuffle,
                    )
                }
            }
        }
    }
}

/** The biography: three lines until select opens it; open, Up and Down scroll it before focus moves on. */
@Composable
private fun Biography(
    text: String,
    modifier: Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val hint = stringResource(if (expanded) R.string.tv_artist_less else R.string.tv_artist_more)
    Surface(
        onClick = { expanded = !expanded },
        modifier =
            modifier
                .widthIn(max = 640.dp)
                .semantics { stateDescription = hint }
                .onPreviewKeyEvent { event ->
                    val down =
                        when (event.key) {
                            Key.DirectionDown -> true
                            Key.DirectionUp -> false
                            else -> return@onPreviewKeyEvent false
                        }
                    // At the text's end (or start) the key moves focus on.
                    val canScroll = if (down) scroll.canScrollForward else scroll.canScrollBackward
                    if (!expanded || !canScroll) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        scope.launch { scroll.animateScrollBy(if (down) SCROLL_STEP else -SCROLL_STEP) }
                    }
                    true
                },
        colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
    ) {
        Text(
            text,
            Modifier
                .padding(8.dp)
                .then(if (expanded) Modifier.heightIn(max = 220.dp).verticalScroll(scroll) else Modifier),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumRow(
    title: String,
    row: String,
    albums: List<Album>,
    focus: FocusMemory,
    onOpenAlbum: (MediaId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MqTvRowHeader(title, Modifier.padding(horizontal = EDGE))
        LazyRow(
            Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = EDGE, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(albums, key = { it.id.toString() }) { album ->
                MqTvAlbumCard(
                    album.title,
                    album.artwork,
                    { onOpenAlbum(album.id) },
                    Modifier.focusMemory(focus, "$row/${album.id}"),
                    subtitle = album.year?.toString(),
                )
            }
        }
    }
}

private val EDGE = 48.dp
private val PICTURE = 180.dp
private const val SCROLL_STEP = 120f
private const val PLAY_ALL = "play all"
private const val BIOGRAPHY = "biography"
