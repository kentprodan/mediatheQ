package io.github.kentprodan.mediatheq.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * Navigation motion (#405, expressive-design skill section 4): the theme's specs, shared axis X. Forward, the new screen
 * comes a little from the end and the old one leaves a little to the start; back is the mirror image; both fade with
 * the effects spec. [rtl] flips the axis. A tab switch is a plain fade: tabs are siblings, not depths.
 */
internal class NavMotion(
    private val spatial: FiniteAnimationSpec<IntOffset>,
    private val effects: FiniteAnimationSpec<Float>,
    private val rtl: Boolean,
) {
    fun forward(tabSwitch: Boolean): ContentTransform = if (tabSwitch) fade() else axis(towardsEnd = true)

    fun back(tabSwitch: Boolean): ContentTransform = if (tabSwitch) fade() else axis(towardsEnd = false)

    /** The back gesture: the leaving screen shrinks a little as it is dragged, the one below fades in. */
    fun predictiveBack(): ContentTransform =
        fadeIn(effects) togetherWith (scaleOut(effects, PREDICTIVE_SCALE) + fadeOut(effects))

    private fun fade(): ContentTransform = fadeIn(effects) togetherWith fadeOut(effects)

    private fun axis(towardsEnd: Boolean): ContentTransform {
        // From the end edge going forward: the right in left-to-right layouts.
        val sign = if (towardsEnd != rtl) 1 else -1
        return (slideInHorizontally(spatial) { width -> sign * width / SLIDE_FRACTION } + fadeIn(effects)) togetherWith
            (slideOutHorizontally(spatial) { width -> -sign * width / SLIDE_FRACTION } + fadeOut(effects))
    }

    private companion object {
        /** A fifth of the width: enough to show the direction, short enough to stay calm. */
        const val SLIDE_FRACTION = 5
        const val PREDICTIVE_SCALE = 0.9f
    }
}
