package io.github.kentprodan.mediatheq.tv.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/**
 * The TV's one back stack (ADR 0041). Home is always at the bottom: a drawer entry replaces everything above it, a
 * detail page pushes. So Back from a detail page pops, from another entry lands on Home, and on Home leaves the app.
 */
class TvNavigator(
    private val backStack: MutableList<NavKey>,
    /** Decides whether [NavKey] may open (#434: the age gate); it calls the continuation when it may. */
    private val guard: ((NavKey, () -> Unit) -> Unit)? = null,
) : Navigator {
    override fun navigateTo(key: NavKey) {
        guard?.invoke(key) { navigate(backStack, key) } ?: navigate(backStack, key)
    }

    override fun back(): Boolean = back(backStack)

    companion object {
        /** [key] onto [stack]: a drawer entry replaces the top level (Home stays below it), anything else pushes. */
        fun navigate(
            stack: MutableList<NavKey>,
            key: NavKey,
        ) {
            if (TvDestination.of(key) == null) {
                if (stack.lastOrNull() != key) stack.add(key)
                return
            }
            stack.clear()
            stack.add(HomeKey)
            if (key != HomeKey) stack.add(key)
        }

        /** One step back; `false` on Home, where the app leaves. */
        fun back(stack: MutableList<NavKey>): Boolean {
            if (stack.size <= 1) return false
            stack.removeAt(stack.lastIndex)
            return true
        }
    }
}
