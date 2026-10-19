package io.github.kentprodan.mediatheq.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The app shell: the current tab's screens, the floating [miniPlayer] slot and the bottom navigation bar.
 * The Scaffold's bottom padding includes the mini player, so content is never hidden behind it.
 */
@Composable
fun AppShell(
    navigator: TabNavigator,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Column {
                if (miniPlayer != null) {
                    Box(
                        Modifier.padding(
                            start = MediatheqSpacing.l,
                            end = MediatheqSpacing.l,
                            bottom = MediatheqSpacing.s,
                        ),
                    ) {
                        miniPlayer()
                    }
                }
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
                        ),
                    entryProvider = entryProvider,
                )
            }
        NavDisplay(
            entries = navigator.visibleTabs.flatMap { entriesByTab.getValue(it) },
            // Consumed, so screens with their own top bar do not add the status bar inset a second time.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onBack = { navigator.back() },
            // Theme motion (expressive-design skill, section 4) instead of NavDisplay's default tween.
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            popTransitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            predictivePopTransitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        )
    }
}
