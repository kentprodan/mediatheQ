package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/**
 * Owns one back stack per [TopLevelTab] (ADR 0005). Switching tabs keeps every stack; re-selecting the current tab
 * pops it to its root. Back pops the current stack, then returns to [TopLevelTab.Home], then lets the app close.
 */
class TabNavigator(
    private val stacks: Map<TopLevelTab, MutableList<NavKey>>,
    currentTab: MutableState<TopLevelTab> = mutableStateOf(TopLevelTab.Home),
) : Navigator {
    var currentTab by currentTab
        private set

    /** Tabs whose entries are shown, bottom first: the start tab, then the current tab. Back walks this order. */
    val visibleTabs: List<TopLevelTab>
        get() = if (currentTab == TopLevelTab.Home) listOf(TopLevelTab.Home) else listOf(TopLevelTab.Home, currentTab)

    /** The back stack of [tab]. */
    fun stack(tab: TopLevelTab): List<NavKey> = stacks.getValue(tab)

    /** Bottom bar click: switch to [tab], or pop it to its root when it is already selected. */
    fun select(tab: TopLevelTab) {
        if (tab == currentTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            currentTab = tab
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
                currentTab = TopLevelTab.Home
                true
            }

            else -> {
                false
            }
        }
    }
}

/** A [TabNavigator] whose stacks and current tab survive configuration changes and process death. */
@Composable
fun rememberTabNavigator(): TabNavigator {
    val stacks = TopLevelTab.entries.associateWith { rememberNavBackStack(it.rootKey) }
    val currentTab = rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    return remember(stacks, currentTab) { TabNavigator(stacks, currentTab) }
}
