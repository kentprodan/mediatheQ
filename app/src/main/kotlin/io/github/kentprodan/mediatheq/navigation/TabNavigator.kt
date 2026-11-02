package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Today's bar, and the default of Settings -> Navigation and media start. */
val DefaultTabs: List<TopLevelTab> = NavigationPreferences.DEFAULT_ENTRIES.map(TopLevelTab::of)

/**
 * Owns one back stack per [TopLevelTab] (ADR 0005). Switching tabs keeps every stack; re-selecting the current tab
 * pops it to its root. Back pops the current stack, then returns to [TopLevelTab.Home], then lets the app close.
 * [tabs] are the ones the navigation shows (#122): a selected tab that is removed falls back to Home, and stacks
 * survive changes of the list.
 */
class TabNavigator(
    private val stacks: Map<TopLevelTab, MutableList<NavKey>>,
    selectedTab: MutableState<TopLevelTab> = mutableStateOf(TopLevelTab.Home),
    private val tabsSource: () -> List<TopLevelTab> = { DefaultTabs },
) : Navigator {
    private var selectedTab by selectedTab

    /** The tabs the navigation shows, in order. */
    val tabs: List<TopLevelTab> get() = tabsSource()

    /** The selected tab, or Home when the selected one is no longer shown. */
    val currentTab: TopLevelTab
        get() = selectedTab.takeIf { it in tabs } ?: TopLevelTab.Home

    /** Tabs whose entries are shown, bottom first: the start tab, then the current tab. Back walks this order. */
    val visibleTabs: List<TopLevelTab>
        get() = currentTab.let { if (it == TopLevelTab.Home) listOf(it) else listOf(TopLevelTab.Home, it) }

    /** The back stack of [tab]. */
    fun stack(tab: TopLevelTab): List<NavKey> = stacks.getValue(tab)

    /** Navigation click: switch to [tab], or pop it to its root when it is already selected. */
    fun select(tab: TopLevelTab) {
        if (tab == currentTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            selectedTab = tab
        }
    }

    override fun navigateTo(key: NavKey) {
        stacks.getValue(currentTab).add(key)
    }

    override fun back(): Boolean {
        val stack = stacks.getValue(currentTab)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            currentTab != TopLevelTab.Home -> {
                selectedTab = TopLevelTab.Home
                true
            }

            else -> {
                false
            }
        }
    }
}

/** A [TabNavigator] showing [tabs], whose stacks and current tab survive configuration changes and process death. */
@Composable
fun rememberTabNavigator(tabs: List<TopLevelTab> = DefaultTabs): TabNavigator {
    val stacks = TopLevelTab.entries.associateWith { rememberNavBackStack(it.rootKey) }
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    val currentTabs by rememberUpdatedState(tabs)
    return remember(stacks, selectedTab) { TabNavigator(stacks, selectedTab) { currentTabs } }
}
