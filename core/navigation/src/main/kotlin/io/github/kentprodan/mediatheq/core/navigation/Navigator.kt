package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey

/** The only way features navigate (ADR 0005). `:app` implements it on top of the per-tab back stacks. */
interface Navigator {
    /** Opens [key] on top of the current tab's back stack. */
    fun navigateTo(key: NavKey)

    /** Goes back one step; returns `false` when there is nothing left to pop (the app may close). */
    fun back(): Boolean
}
