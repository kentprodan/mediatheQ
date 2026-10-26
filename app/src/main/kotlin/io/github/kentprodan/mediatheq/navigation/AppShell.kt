package io.github.kentprodan.mediatheq.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.github.kentprodan.mediatheq.R
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalNavAnimatedVisibilityScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalSharedTransitionScope
import io.github.kentprodan.mediatheq.core.navigation.AudioSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey

/** Pages that need no network: they stay usable offline, so Log out is always reachable (#80). */
private val OfflineKeys = setOf<NavKey>(SettingsKey, AudioSettingsKey, ThemeShowcaseKey)

/**
 * The app shell: the current tab's screens, the floating [miniPlayer] slot and the bottom navigation bar.
 * The Scaffold's bottom padding includes the mini player, so content is never hidden behind it.
 * While [offline], every tab but Downloads shows the offline state (only downloads play then), with a gear that opens
 * Settings on the current tab; Settings and its pages ([OfflineKeys]) keep working offline.
 */
@Composable
fun AppShell(
    navigator: TabNavigator,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    miniPlayer: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        bottomBar = {
            Column {
                // Places and animates itself (it is hidden while nothing is queued).
                miniPlayer?.invoke()
                ShortNavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        val selected = tab == navigator.currentTab
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { navigator.select(tab) },
                            icon = { Icon(tab.icon(selected), contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        // Hands each screen its enter/exit scope as a nullable local, so shared covers work there and stay inert
        // elsewhere (Navigation 3's own local throws outside NavDisplay).
        val animatedScopeDecorator =
            remember {
                NavEntryDecorator<NavKey>(onPop = {}) { entry ->
                    CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                    ) {
                        entry.Content()
                    }
                }
            }
        // Each tab decorates its own stack, so a hidden tab keeps its screens' saved state (e.g. scroll position).
        val entriesByTab =
            TopLevelTab.entries.associateWith { tab ->
                rememberDecoratedNavEntries(
                    backStack = navigator.stack(tab),
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            // ViewModels live as long as their entry, also while their tab is hidden.
                            rememberViewModelStoreNavEntryDecorator(),
                            animatedScopeDecorator,
                        ),
                    entryProvider = entryProvider,
                )
            }
        val tab = navigator.currentTab
        if (offline && tab != TopLevelTab.Downloads && navigator.stack(tab).last() !in OfflineKeys) {
            // The tabs' stacks and saved state stay as they are; they come back with the network. Back still walks
            // them (NavDisplay, which handles it otherwise, is not composed meanwhile).
            BackHandler(navigator.stack(tab).size > 1 || tab != TopLevelTab.Home) {
                navigator.back()
            }
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                TopAppBar(
                    title = { Text(stringResource(tab.label)) },
                    actions = {
                        IconButton(onClick = { navigator.navigateTo(SettingsKey) }) {
                            Icon(MqIcons.Settings, contentDescription = stringResource(R.string.offline_settings))
                        }
                    },
                )
                MqEmptyState(
                    icon = MqIcons.CloudOff,
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.offline_body),
                    actionLabel = stringResource(R.string.offline_open_downloads),
                    onAction = { navigator.select(TopLevelTab.Downloads) },
                )
            }
            return@Scaffold
        }
        // Covers fly between screens of a tab (grid -> album). Inside the Scaffold, so they never draw over the bar.
        SharedTransitionLayout(Modifier.padding(padding).consumeWindowInsets(padding)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavDisplay(
                    entries = navigator.visibleTabs.flatMap { entriesByTab.getValue(it) },
                    sharedTransitionScope = this,
                    onBack = { navigator.back() },
                    // Entries marked as dialogs (Add to playlist) show in a dialog window over the screen below.
                    sceneStrategies = listOf(DialogSceneStrategy()),
                    // Theme motion (expressive-design skill, section 4) instead of NavDisplay's default tween.
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    popTransitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    predictivePopTransitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                )
            }
        }
    }
}
