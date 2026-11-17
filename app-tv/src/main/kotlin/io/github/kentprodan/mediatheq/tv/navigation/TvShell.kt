package io.github.kentprodan.mediatheq.tv.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvPinDialog
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvCover
import io.github.kentprodan.mediatheq.feature.tv.album.tvAlbumEntries
import io.github.kentprodan.mediatheq.feature.tv.artist.tvArtistEntry
import io.github.kentprodan.mediatheq.feature.tv.home.tvHomeEntry
import io.github.kentprodan.mediatheq.feature.tv.library.tvLibraryEntries
import io.github.kentprodan.mediatheq.feature.tv.nowplaying.tvNowPlayingEntry
import io.github.kentprodan.mediatheq.feature.tv.search.tvSearchEntry
import io.github.kentprodan.mediatheq.feature.tv.settings.tvSettingsEntries
import io.github.kentprodan.mediatheq.feature.tv.video.tvVideoEntries
import io.github.kentprodan.mediatheq.tv.R

@Composable
internal fun TvShellRoute(
    openPlayer: Boolean = false,
    onPlayerOpened: () -> Unit = {},
    openPage: NavKey? = null,
    onPageOpened: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    viewModel: TvShellViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val simpleTransitions by viewModel.simpleTransitions.collectAsStateWithLifecycle()
    val pin by viewModel.pinRequest.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(HomeKey)
    val context = LocalContext.current
    val versionName =
        remember {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }

    // The build's licenses report and NOTICE files (ADR 0022), read when the licenses list opens.
    fun raw(id: Int) =
        context.resources
            .openRawResource(id)
            .bufferedReader()
            .use { it.readText() }
    // A movie sent from the phone or Jellyfin web (#389) plays already: its player opens on it.
    LaunchedEffect(Unit) {
        viewModel.remoteVideo.collect { id -> openRemoteVideo(backStack, id) }
    }
    // A system search suggestion (#386): its page over what is open.
    LaunchedEffect(openPage) {
        openPage?.let {
            TvNavigator.navigate(backStack, it)
            onPageOpened()
        }
    }
    // The launcher's Now Playing card (#240).
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            TvNavigator.navigate(backStack, NowPlayingKey)
            onPlayerOpened()
        }
    }
    val activity = LocalActivity.current
    TvShell(
        backStack,
        player,
        destinations = destinations,
        simpleTransitions = simpleTransitions,
        onUnknown = viewModel::unknownDestination,
        onClose = {
            viewModel.stopMusic()
            activity?.finish()
        },
        guard = viewModel::guard,
    ) { navigator ->
        tvHomeEntry(navigator)
        tvLibraryEntries(navigator)
        tvAlbumEntries()
        tvArtistEntry(navigator)
        tvNowPlayingEntry()
        tvSettingsEntries(
            navigator,
            versionName,
            report = { raw(R.raw.aboutlibraries) },
            notices = { raw(R.raw.license_notices) },
            onSwitchProfile = onSwitchProfile,
        )
        tvSearchEntry(navigator)
        tvVideoEntries(navigator)
    }
    // The age gate (#434): content above this profile's limit, and Parental controls, ask for the PIN.
    pin?.let { request ->
        MqTvPinDialog(
            stringResource(R.string.tv_pin_title),
            stringResource(R.string.tv_pin_label),
            onSubmit = viewModel::submitPin,
            onDismiss = viewModel::cancelPin,
            error = if (request.wrong) stringResource(R.string.tv_pin_wrong) else null,
            onEdit = viewModel::pinEdited,
        )
    }
}

/**
 * The TV frame (ADR 0041, #508): a tab bar at the top on the top-level screens (the now-playing chip at its end), the
 * top of the one back stack under it. [entries] registers the screens; a key without a screen shows
 * a placeholder and is logged once. Back pops; on Home it asks whether to close the app (#343): [onClose] stops the music
 * and leaves. The remote's Home button leaves with the music going on.
 */
@Composable
fun TvShell(
    backStack: MutableList<NavKey>,
    player: PlayerUiModel,
    destinations: List<TvDestination> = TvDestination.entries,
    simpleTransitions: Boolean = false,
    onUnknown: (NavKey) -> Unit = {},
    onClose: () -> Unit = {},
    guard: ((NavKey, () -> Unit) -> Unit)? = null,
    entries: EntryProviderScope<NavKey>.(TvNavigator) -> Unit,
) {
    val navigator = remember(backStack) { TvNavigator(backStack, guard) }
    var closing by remember { mutableStateOf(false) }
    BackHandler(enabled = backStack.size <= 1) { closing = true }
    if (closing) {
        MqTvMenu(
            stringResource(R.string.tv_close_title),
            listOf(
                MqTvMenuEntry(stringResource(R.string.tv_close), null, onClose),
                MqTvMenuEntry(stringResource(R.string.tv_close_cancel), null) {},
            ),
            onDismiss = { closing = false },
        )
    }
    // The mode hid an entry (Settings -> Show): its screen and what it opened leave the stack, down to Home.
    LaunchedEffect(destinations) {
        val hidden = backStack.indexOfFirst { key -> TvDestination.of(key)?.let { it !in destinations } == true }
        if (hidden > 0) repeat(backStack.size - hidden) { backStack.removeAt(backStack.lastIndex) }
    }
    val selected = backStack.lastOrNull { TvDestination.of(it) != null }?.let(TvDestination::of) ?: TvDestination.Home
    // The tabs show on the top level only (#508): a detail page and the players have the whole screen (#515).
    val topLevel = backStack.lastOrNull()?.let(TvDestination::of) != null
    TvFrame(selected, player, navigator::navigateTo, destinations, showTabs = topLevel) {
        // Each screen requests its own initial focus once, the item it had before when it comes back (FocusMemory).
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.back() },
            transitionSpec = { TvMotion.forward(simpleTransitions) },
            popTransitionSpec = { TvMotion.back(simpleTransitions) },
            entryDecorators =
                listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider(
                    fallback = { key ->
                        NavEntry(key) {
                            // The provider runs again on every stack change: log when the entry shows, not per call.
                            LaunchedEffect(key) { onUnknown(key) }
                            TvPlaceholder(stringResource(R.string.app_name))
                        }
                    },
                ) { entries(navigator) },
        )
    }
}

/**
 * The tab bar over [content] (#508, owner decision: Google TV / Netflix style instead of a side drawer): the
 * destinations with [selected] marked, then what plays. Shown only when [showTabs]; a tab opens on Select, not on
 * focus, so moving along the bar loads nothing.
 */
@Composable
fun TvFrame(
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
    destinations: List<TvDestination> = TvDestination.entries,
    showTabs: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (showTabs) TabBar(selected, player, onNavigate, destinations)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun TabBar(
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
    destinations: List<TvDestination>,
) {
    // Focus coming up from the content lands on the selected tab, not on the one focused last (ADR 0041).
    val requesters = remember { TvDestination.entries.associateWith { FocusRequester() } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up) {
                        requesters.getValue(selected.takeIf { it in destinations } ?: TvDestination.Home).requestFocus()
                    }
                }
            }.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { destination ->
            Tab(
                stringResource(destination.label),
                destination.icon,
                destination == selected,
                Modifier.focusRequester(requesters.getValue(destination)),
            ) { onNavigate(destination.key) }
        }
        Spacer(Modifier.weight(1f))
        player.item?.let { item ->
            val playState = stringResource(if (player.isPlaying) R.string.tv_playing else R.string.tv_paused)
            // What plays: its cover, title and state; Select opens Now playing.
            Surface(
                onClick = { onNavigate(TvDestination.NowPlaying.key) },
                modifier = Modifier.widthIn(max = 320.dp).semantics { stateDescription = playState },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = tabColors(selected = false),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MqTvCover(
                        item.artwork,
                        MqTvIcons.MusicNote,
                        Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall),
                    )
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        if (player.isPlaying) MqTvIcons.Pause else MqTvIcons.Play,
                        contentDescription = null,
                        Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = tabColors(selected),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun tabColors(selected: Boolean): ClickableSurfaceColors {
    val scheme = MaterialTheme.colorScheme
    return ClickableSurfaceDefaults.colors(
        containerColor = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    )
}

/** A movie sent from another client (#389): its player opens, in place of a player already on top (never two). */
internal fun openRemoteVideo(
    backStack: MutableList<NavKey>,
    id: io.github.kentprodan.mediatheq.core.model.MediaId,
) {
    if (backStack.lastOrNull() is VideoPlayerKey) backStack.removeAt(backStack.lastIndex)
    TvNavigator.navigate(backStack, VideoPlayerKey(id, attach = true))
}
