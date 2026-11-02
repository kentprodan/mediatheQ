package io.github.kentprodan.mediatheq.navigation

import android.content.res.Configuration
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import io.github.kentprodan.mediatheq.core.data.LandscapeNavStyle
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
import io.github.kentprodan.mediatheq.core.designsystem.component.LocalBottomBarsPadding
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalNavAnimatedVisibilityScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalSharedTransitionScope
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The app shell: the current tab's screens, the floating [miniPlayer] slot and the main navigation, styled as
 * [navigation] says (#122): a bottom bar or a floating toolbar (with the last entry as a FAB) in portrait, a rail on the
 * left in landscape. Screens scroll under the bottom bars, which can be see-through; their height reaches the screens
 * as [LocalBottomBarsPadding], so lists end above them. With only Home there is no navigation at all.
 * Offline, the tabs show what is downloaded (offline mode, #63).
 */
@Composable
fun AppShell(
    navigator: TabNavigator,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    navigation: NavigationPreferences = NavigationPreferences(),
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showNavigation = navigator.tabs.size > 1
    val rail = showNavigation && landscape && navigation.landscapeStyle == LandscapeNavStyle.NavigationRail
    val containerAlpha = 1f - navigation.transparencyPercent / 100f
    Row(modifier) {
        if (rail) NavigationRail(navigator)
        Scaffold(
            // The rail already keeps clear of the system bars on its side.
            modifier =
                if (rail) {
                    Modifier.consumeWindowInsets(
                        WindowInsets.systemBars.only(WindowInsetsSides.Start),
                    )
                } else {
                    Modifier
                },
            snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
            bottomBar = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Places and animates itself (it is hidden while nothing is queued).
                    miniPlayer?.invoke()
                    when {
                        !showNavigation || rail -> {
                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }

                        navigation.portraitStyle == PortraitNavStyle.BottomBar -> {
                            BottomBar(navigator, containerAlpha)
                        }

                        else -> {
                            FloatingNavigation(
                                navigator,
                                containerAlpha,
                                withFab = navigation.portraitStyle == PortraitNavStyle.FloatingToolbarWithFab,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            ShellContent(navigator, entryProvider, padding)
        }
    }
}

@Composable
private fun ShellContent(
    navigator: TabNavigator,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    padding: PaddingValues,
) {
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
    // Only the shown tabs (#122); a tab removed from the navigation drops its screens' saved state.
    val entriesByTab =
        navigator.tabs.associateWith { tab ->
            key(tab) {
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
        }
    // Screens go under the bottom bars (only the top and sides are padded); they pad their lists by the rest.
    val direction = LocalLayoutDirection.current
    val aboveBars =
        PaddingValues(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(direction),
        )
    // Covers fly between screens of a tab (grid -> album). Inside the Scaffold, so they never draw over the bar.
    SharedTransitionLayout(Modifier.padding(aboveBars).consumeWindowInsets(aboveBars)) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalBottomBarsPadding provides padding.calculateBottomPadding(),
        ) {
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

/** Labels only when they fit: up to [FULL_LABEL_TABS] tabs, or a font that is not much enlarged. */
internal fun showsLabels(
    tabs: Int,
    fontScale: Float,
): Boolean = tabs <= FULL_LABEL_TABS || fontScale < LARGE_FONT_SCALE

private const val FULL_LABEL_TABS = 4
private const val LARGE_FONT_SCALE = 1.5f

@Composable
private fun BottomBar(
    navigator: TabNavigator,
    containerAlpha: Float,
) {
    val labels = showsLabels(navigator.tabs.size, LocalDensity.current.fontScale)
    ShortNavigationBar(containerColor = ShortNavigationBarDefaults.containerColor.copy(alpha = containerAlpha)) {
        navigator.tabs.forEach { tab ->
            val selected = tab == navigator.currentTab
            val label = stringResource(tab.label)
            ShortNavigationBarItem(
                selected = selected,
                onClick = { navigator.select(tab) },
                // Without a label the icon names the tab.
                icon = { Icon(tab.icon(selected), contentDescription = if (labels) null else label) },
                label = if (labels) ({ Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
            )
        }
    }
}

/** The M3 Expressive floating toolbar of icon tabs; [withFab] makes the last tab a FAB next to it. */
@Composable
private fun FloatingNavigation(
    navigator: TabNavigator,
    containerAlpha: Float,
    withFab: Boolean,
) {
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
    val toolbarColors =
        FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = colors.toolbarContainerColor.copy(alpha = containerAlpha),
        )
    val tabs = if (withFab) navigator.tabs.dropLast(1) else navigator.tabs
    val modifier = Modifier.navigationBarsPadding().padding(bottom = MediatheqSpacing.s)
    if (withFab) {
        val fabTab = navigator.tabs.last()
        val selected = fabTab == navigator.currentTab
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(
                    onClick = { navigator.select(fabTab) },
                    modifier = Modifier.tabSemantics(selected),
                ) { Icon(fabTab.icon(selected), contentDescription = stringResource(fabTab.label)) }
            },
            modifier = modifier,
            colors = toolbarColors,
        ) { tabs.forEach { ToolbarTab(it, navigator) } }
    } else {
        HorizontalFloatingToolbar(expanded = true, modifier = modifier, colors = toolbarColors) {
            tabs.forEach { ToolbarTab(it, navigator) }
        }
    }
}

@Composable
private fun ToolbarTab(
    tab: TopLevelTab,
    navigator: TabNavigator,
) {
    val selected = tab == navigator.currentTab
    val icon = @Composable { Icon(tab.icon(selected), contentDescription = stringResource(tab.label)) }
    // The selected tab gets a tonal container, so it is not shown by color alone.
    if (selected) {
        FilledTonalIconButton(onClick = { navigator.select(tab) }, Modifier.tabSemantics(true), content = icon)
    } else {
        IconButton(onClick = { navigator.select(tab) }, Modifier.tabSemantics(false), content = icon)
    }
}

private fun Modifier.tabSemantics(selected: Boolean) =
    semantics {
        role = Role.Tab
        this.selected = selected
    }

/** The collapsed rail on the left, for landscape. */
@Composable
private fun NavigationRail(navigator: TabNavigator) {
    WideNavigationRail {
        navigator.tabs.forEach { tab ->
            val selected = tab == navigator.currentTab
            WideNavigationRailItem(
                selected = selected,
                onClick = { navigator.select(tab) },
                icon = { Icon(tab.icon(selected), contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                railExpanded = false,
            )
        }
    }
}
