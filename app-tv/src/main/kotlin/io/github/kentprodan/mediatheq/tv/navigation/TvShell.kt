package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvCover
import io.github.kentprodan.mediatheq.feature.tv.album.tvAlbumEntries
import io.github.kentprodan.mediatheq.feature.tv.artist.tvArtistEntry
import io.github.kentprodan.mediatheq.feature.tv.home.tvHomeEntry
import io.github.kentprodan.mediatheq.feature.tv.library.tvLibraryEntries
import io.github.kentprodan.mediatheq.feature.tv.nowplaying.tvNowPlayingEntry
import io.github.kentprodan.mediatheq.feature.tv.settings.tvSettingsEntries
import io.github.kentprodan.mediatheq.tv.R

@Composable
internal fun TvShellRoute(
    openPlayer: Boolean = false,
    onPlayerOpened: () -> Unit = {},
    viewModel: TvShellViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
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
    // The launcher's Now Playing card (#240).
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            TvNavigator.navigate(backStack, NowPlayingKey)
            onPlayerOpened()
        }
    }
    TvShell(backStack, player, onUnknown = viewModel::unknownDestination) { navigator ->
        tvHomeEntry(navigator)
        tvLibraryEntries(navigator)
        tvAlbumEntries()
        tvArtistEntry(navigator)
        tvNowPlayingEntry()
        tvSettingsEntries(navigator, versionName, report = {
            raw(R.raw.aboutlibraries)
        }, notices = { raw(R.raw.license_notices) })
        tvPlaceholderEntries()
    }
}

/**
 * The TV frame (ADR 0041): the drawer on the left (collapsed to icons until focused, the now-playing strip in its
 * footer), the top of the one back stack on the right. [entries] registers the screens; a key without a screen shows
 * a placeholder and is logged once. Back pops; on Home the shell does not handle it, so the activity's own Back leaves.
 */
@Composable
fun TvShell(
    backStack: MutableList<NavKey>,
    player: PlayerUiModel,
    onUnknown: (NavKey) -> Unit = {},
    initialDrawer: DrawerValue = DrawerValue.Closed,
    entries: EntryProviderScope<NavKey>.(TvNavigator) -> Unit,
) {
    val navigator = remember(backStack) { TvNavigator(backStack) }
    val selected = backStack.lastOrNull { TvDestination.of(it) != null }?.let(TvDestination::of) ?: TvDestination.Home
    TvFrame(selected, player, navigator::navigateTo, initialDrawer) {
        // Each screen requests its own initial focus once, the item it had before when it comes back (FocusMemory).
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.back() },
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
 * The drawer around [content]: the entries with [selected] marked, the strip when [player] has something queued.
 * [initialDrawer] is for previews (the app starts closed; the drawer opens when focus enters it).
 */
@Composable
fun TvFrame(
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
    initialDrawer: DrawerValue = DrawerValue.Closed,
    content: @Composable () -> Unit,
) {
    NavigationDrawer(
        drawerContent = { drawer -> Drawer(drawer, selected, player, onNavigate) },
        drawerState = rememberDrawerState(initialDrawer),
    ) { content() }
}

@Composable
private fun NavigationDrawerScope.Drawer(
    drawer: DrawerValue,
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
) {
    // Focus coming back from the content lands on the selected entry, not on the one focused last (ADR 0041).
    val requesters = remember { TvDestination.entries.associateWith { FocusRequester() } }
    Column(
        Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .focusProperties { onEnter = { requesters.getValue(selected).requestFocus() } },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                selected = destination == selected,
                onClick = { onNavigate(destination.key) },
                modifier = Modifier.focusRequester(requesters.getValue(destination)),
                leadingContent = { Icon(destination.icon, contentDescription = null) },
            ) { Text(stringResource(destination.label)) }
        }
        Spacer(Modifier.weight(1f))
        player.item?.let { item ->
            val playState = stringResource(if (player.isPlaying) R.string.tv_playing else R.string.tv_paused)
            // The strip: the cover (collapsed, the only part shown), what plays and its play state; selecting it opens
            // Now playing.
            NavigationDrawerItem(
                selected = false,
                onClick = { onNavigate(TvDestination.NowPlaying.key) },
                modifier = Modifier.semantics { stateDescription = playState },
                leadingContent = {
                    MqTvCover(
                        item.artwork,
                        MqTvIcons.MusicNote,
                        Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall),
                    )
                },
                trailingContent = {
                    Icon(if (player.isPlaying) MqTvIcons.Pause else MqTvIcons.Play, contentDescription = null)
                },
                supportingContent = {
                    if (drawer == DrawerValue.Open) Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            ) { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
