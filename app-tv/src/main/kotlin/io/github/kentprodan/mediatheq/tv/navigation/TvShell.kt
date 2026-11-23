package io.github.kentprodan.mediatheq.tv.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvPinDialog
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.ui.tv.LocalTvPosterAccent
import io.github.kentprodan.mediatheq.core.ui.tv.LocalTvTitleStyle
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAvatar
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

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
    val titleStyle by viewModel.titleStyle.collectAsStateWithLifecycle()
    val posterAccent by viewModel.posterAccent.collectAsStateWithLifecycle()
    val pin by viewModel.pinRequest.collectAsStateWithLifecycle()
    val menuStyle by viewModel.menuStyle.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userImage by viewModel.userImage.collectAsStateWithLifecycle()
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
    // The detail pages read the title style (#523).
    CompositionLocalProvider(LocalTvTitleStyle provides titleStyle, LocalTvPosterAccent provides posterAccent) {
        TvShell(
            backStack,
            player,
            destinations = destinations,
            simpleTransitions = simpleTransitions,
            menuStyle = menuStyle,
            userName = userName,
            userImage = userImage,
            onProfile = onSwitchProfile,
            onUnknown = viewModel::unknownDestination,
            onClose = {
                viewModel.close()
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
 * The TV frame (ADR 0041, #508): on the top-level screens a small menu floats at the left over the screen, Plex style;
 * the top of the one back stack fills the screen. [entries] registers the screens; a key without a screen shows
 * a placeholder and is logged once. Back pops; on Home it asks whether to close the app (#343): [onClose] stops the music
 * and leaves. The remote's Home button leaves with the music going on.
 */
@Composable
fun TvShell(
    backStack: MutableList<NavKey>,
    player: PlayerUiModel,
    destinations: List<TvDestination> = TvDestination.entries,
    simpleTransitions: Boolean = false,
    menuStyle: TvMenuStyle = TvMenuStyle.Modal,
    userName: String? = null,
    userImage: String? = null,
    onProfile: () -> Unit = {},
    onUnknown: (NavKey) -> Unit = {},
    onClose: () -> Unit = {},
    guard: ((NavKey, () -> Unit) -> Unit)? = null,
    entries: EntryProviderScope<NavKey>.(TvNavigator) -> Unit,
) {
    val navigator = remember(backStack) { TvNavigator(backStack, guard) }
    val menu = remember { TvMenuState() }
    var closing by remember { mutableStateOf(false) }
    BackHandler(enabled = backStack.size <= 1) { closing = true }
    if (closing) {
        MqTvMenu(
            stringResource(R.string.tv_close_title),
            listOf(
                MqTvMenuEntry(stringResource(R.string.tv_close), null, onClick = onClose),
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
    // The menu shows on the top level only (#508): a detail page and the players have the whole screen (#515).
    val topLevel = backStack.lastOrNull()?.let(TvDestination::of) != null
    TvFrame(
        selected,
        player,
        navigator::navigateTo,
        destinations,
        showMenu = topLevel,
        menuStyle = menuStyle,
        userName = userName,
        userImage = userImage,
        onProfile = onProfile,
        state = menu,
    ) {
        // Each screen requests its own initial focus once, the item it had before when it comes back (FocusMemory).
        NavDisplay(
            backStack = backStack,
            onBack = {
                // Back from the open menu (#567): it stays open on the new screen's entry.
                val fromMenu = menu.open
                navigator.back()
                if (fromMenu) menu.reopen = true
            },
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

/** The menu's focus (#567): whether it is open, and focusing the active entry. */
class TvMenuState {
    internal val requesters = TvDestination.entries.associateWith { FocusRequester() }
    internal var active = TvDestination.Home

    /** Back popped while the menu was open: it focuses the new active entry once the screen under it has settled. */
    internal var reopen by mutableStateOf(false)

    /** The menu holds focus. */
    var open by mutableStateOf(false)
        internal set

    fun focusActive() {
        runCatching { requesters.getValue(active).requestFocus() }
    }
}

/**
 * The menu over [content] (#508, owner decision: Plex's 2026 side navigation): collapsed, a small floating island of
 * icons at the left; Left from the screen or Back opens it with the labels and what plays, the selected entry focused
 * (Android TV navigation guidelines). An entry opens on Select. [menuStyle] (#520): Modal opens it over the dimmed
 * screen (nothing moves), Standard pushes the screen aside. Shown only when [showMenu]; the screen keeps [MENU_SPACE]
 * free at its left for the island. [userName] adds the profile entry at the top ([onProfile]: Who's watching).
 */
@Composable
fun TvFrame(
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
    destinations: List<TvDestination> = TvDestination.entries,
    showMenu: Boolean = true,
    menuStyle: TvMenuStyle = TvMenuStyle.Modal,
    userName: String? = null,
    userImage: String? = null,
    onProfile: () -> Unit = {},
    previewOpen: Boolean = false,
    state: TvMenuState = remember { TvMenuState() },
    content: @Composable () -> Unit,
) {
    val requesters = state.requesters
    val screen = remember { FocusRequester() }
    // Previews render no focus: they open the menu with [previewOpen].
    val open = state.open || previewOpen
    // The island's right edge, read only while drawing (Standard): opening moves the screen without a new layout.
    var islandEnd by remember { mutableIntStateOf(0) }
    val standard = menuStyle == TvMenuStyle.Standard
    val active = selected.takeIf { it in destinations } ?: TvDestination.Home
    SideEffect { state.active = active }
    // The screen under the open menu changed by Back (#567): focus may stay on the profile, or the screen takes its own
    // first focus (at once, or once its content loads), which would close the menu. Two frames later the menu takes
    // focus on the new active entry, and for a moment after that any grab by the screen is sent back to it.
    LaunchedEffect(state.reopen) {
        if (!state.reopen) return@LaunchedEffect
        repeat(2) { withFrameNanos {} }
        state.focusActive()
        delay(REOPEN_GUARD)
        state.reopen = false
    }
    // How far the screen is pushed (Standard), 0..1 with the island's own size animation (#568): closing slides it back
    // with the island instead of snapping to 0 while the island still shrinks.
    val push by animateFloatAsState(
        if (showMenu && standard && open) 1f else 0f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "menuPush",
    )
    // The neutral background also behind the island's margin (the window's own color would show there).
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The screen stays at the same place in the tree with or without the menu, so it keeps its state.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (showMenu) MENU_SPACE else 0.dp)
                .graphicsLayer {
                    // The screen's first column starts MENU_SPACE + its own 48 dp in: it moves past the island. Read
                    // here, the animation redraws the layer only.
                    translationX =
                        push * (islandEnd + PUSH_GAP.toPx() - (MENU_SPACE + SCREEN_MARGIN).toPx()).coerceAtLeast(0f)
                }.onFocusChanged { if (it.hasFocus && state.reopen) state.focusActive() }
                .focusRequester(screen)
                .focusRestorer()
                .focusGroup(),
        ) { content() }
        if (!showMenu) return@Box
        if (open && !standard) {
            // Modal: the screen dims behind the open menu.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))),
            )
        }
        Island(
            open,
            selected,
            player,
            onNavigate,
            destinations,
            requesters,
            userName,
            userImage,
            onProfile,
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                // Measured while laying out, so the same frame already draws the pushed screen.
                .layout { measurable, constraints ->
                    val island = measurable.measure(constraints)
                    islandEnd = 16.dp.roundToPx() + island.width
                    layout(island.width, island.height) { island.place(0, 0) }
                }.onFocusChanged { state.open = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    // A key pressed in the menu ends the guard after Back: the person moves on their own now.
                    if (event.type == KeyEventType.KeyDown) state.reopen = false
                    // The open menu covers the screen's first column: Right goes back to what the screen had focused.
                    val right = event.key == Key.DirectionRight
                    if (right && event.type == KeyEventType.KeyDown) screen.requestFocus()
                    right
                }.focusProperties {
                    // Left from the screen: the selected entry, not the one focused last (ADR 0041); a focus asked for
                    // in code (an entry itself) is left alone.
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Left) requesters.getValue(active).requestFocus()
                    }
                }.focusGroup(),
        )
    }
    // Back from the screen opens the menu on the selected entry (Android TV guidelines); from the menu, Back goes on
    // as before (Home, then the close question).
    BackHandler(enabled = showMenu && !open) { state.focusActive() }
}

@Composable
private fun Island(
    open: Boolean,
    selected: TvDestination,
    player: PlayerUiModel,
    onNavigate: (NavKey) -> Unit,
    destinations: List<TvDestination>,
    requesters: Map<TvDestination, FocusRequester>,
    userName: String?,
    userImage: String?,
    onProfile: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(28.dp),
        colors =
            SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (open) 0.98f else 0.85f),
            ),
    ) {
        Column(
            Modifier.padding(8.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The guideline's anatomy: Search in the top section, the destinations, Settings in the bottom section.
            val top = destinations.filter { it == TvDestination.Search }
            val bottom = destinations.filter { it == TvDestination.Settings }
            val entry: @Composable (TvDestination) -> Unit = { destination ->
                MenuEntry(
                    stringResource(destination.label),
                    destination.icon,
                    destination == selected,
                    open,
                    Modifier.focusRequester(requesters.getValue(destination)),
                ) { onNavigate(destination.key) }
            }
            // Who is watching first (#520): Select opens the profile picker.
            userName?.takeIf { it.isNotBlank() }?.let { name -> ProfileEntry(name, userImage, open, onProfile) }
            top.forEach { entry(it) }
            if (top.isNotEmpty()) Spacer(Modifier.height(12.dp))
            // Now playing has no entry of its own (#573): the pill below opens it.
            (destinations - (top + bottom).toSet() - TvDestination.NowPlaying).forEach { entry(it) }
            if (bottom.isNotEmpty()) Spacer(Modifier.height(12.dp))
            bottom.forEach { entry(it) }
            player.item?.let { item ->
                // Set apart from Settings: what plays, not another entry.
                Spacer(Modifier.height(12.dp))
                val playState = stringResource(if (player.isPlaying) R.string.tv_playing else R.string.tv_paused)
                // What plays: its cover (and title once open); Select opens Now playing, which it marks as the active
                // entry there (#573).
                Surface(
                    onClick = { onNavigate(TvDestination.NowPlaying.key) },
                    modifier =
                        Modifier
                            .focusRequester(requesters.getValue(TvDestination.NowPlaying))
                            .semantics {
                                stateDescription = playState
                                this.selected = selected == TvDestination.NowPlaying
                            },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                    colors = entryColors(selected = selected == TvDestination.NowPlaying),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MqTvCover(
                            item.artwork,
                            MqTvIcons.MusicNote,
                            Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall),
                        )
                        if (open) {
                            Text(
                                item.title,
                                Modifier.widthIn(max = 180.dp),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The signed-in user's picture (#556), the initial without one (the guideline's avatar), the name once open. */
@Composable
private fun ProfileEntry(
    name: String,
    image: String?,
    open: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.tv_nav_profile, name)
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = entryColors(selected = false),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            // The entry's description says it all: the initial and the name are not read again.
            Modifier.padding(10.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MqTvAvatar(name, image, 24.dp, MaterialTheme.typography.labelMedium)
            if (open) {
                Text(
                    name,
                    Modifier.widthIn(max = 180.dp).padding(end = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    open: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = entryColors(selected),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Collapsed, the label is still there for TalkBack.
            Icon(icon, contentDescription = if (open) null else label, Modifier.size(24.dp))
            if (open) Text(label, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun entryColors(selected: Boolean): ClickableSurfaceColors {
    val scheme = MaterialTheme.colorScheme
    return ClickableSurfaceDefaults.colors(
        // The accent marks the active entry only (#517).
        containerColor = if (selected) scheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        focusedContainerColor = scheme.inverseSurface,
        focusedContentColor = scheme.inverseOnSurface,
    )
}

/** Added to the screens' own 48 dp margin, so the collapsed island never covers their first column. */
private val MENU_SPACE = 32.dp

/** The screens' own start margin. */
private val SCREEN_MARGIN = 48.dp

/** How long after Back from the open menu a screen's late first focus is sent back to the menu (#567). */
private val REOPEN_GUARD = 1.seconds

/** Between the open island and the pushed screen (Standard, #520). */
private val PUSH_GAP = 24.dp

/** A movie sent from another client (#389): its player opens, in place of a player already on top (never two). */
internal fun openRemoteVideo(
    backStack: MutableList<NavKey>,
    id: io.github.kentprodan.mediatheq.core.model.MediaId,
) {
    if (backStack.lastOrNull() is VideoPlayerKey) backStack.removeAt(backStack.lastIndex)
    TvNavigator.navigate(backStack, VideoPlayerKey(id, attach = true))
}
