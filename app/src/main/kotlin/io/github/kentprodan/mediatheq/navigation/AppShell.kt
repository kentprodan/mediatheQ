package io.github.kentprodan.mediatheq.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
        // Each tab decorates its own stack, so a hidden tab keeps its screens' saved state (e.g. scroll position).
        val entriesByTab =
            TopLevelTab.entries.associateWith { tab ->
                rememberDecoratedNavEntries(
                    backStack = navigator.stack(tab),
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = entryProvider,
                )
            }
        NavDisplay(
            entries = navigator.visibleTabs.flatMap { entriesByTab.getValue(it) },
            modifier = Modifier.padding(padding),
            onBack = { navigator.back() },
        )
    }
}
