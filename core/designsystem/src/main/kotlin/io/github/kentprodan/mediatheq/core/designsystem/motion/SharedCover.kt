package io.github.kentprodan.mediatheq.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** The app shell's shared-transition scope; `null` outside the shell (previews, tests). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The enclosing enter/exit animation scope (a navigation entry or the player overlay); `null` elsewhere. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Makes a cover fly between screens: two covers with the same [key] (e.g. `"cover-<MediaId>"`) on the leaving and the
 * entering screen animate into each other, using the theme's slow spatial motion (expressive-design skill, section 4).
 * Does nothing where there is no shell or no screen transition, so previews and tests render normally.
 */
@Composable
fun Modifier.mqSharedCover(
    key: Any,
    /**
     * With no cover of this key on the other screen, draw it above the flying covers while this screen comes in (the
     * album's artist picture on the album cover, #681); else it would show only once the album cover landed.
     */
    aboveCoversWhenAlone: Boolean = false,
    /** Above the other shared covers in flight (the back button over a landing cover). */
    zIndexInOverlay: Float = 0f,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    return with(shared) {
        val state = rememberSharedContentState(key)
        val above =
            if (aboveCoversWhenAlone) {
                Modifier.renderInSharedTransitionScopeOverlay(
                    renderInOverlay = { isTransitionActive && !state.isMatchFound && visibility.isEntering() },
                    zIndexInOverlay = 1f,
                )
            } else {
                Modifier
            }
        this@mqSharedCover.then(above).sharedElement(
            state,
            visibility,
            boundsTransform =
                BoundsTransform {
                    _,
                    _,
                    ->
                    spec
                },
        )
    }
}

/**
 * Draws this above the covers flying between screens (#681), e.g. the top bar's back button over an album cover that
 * lands under it: without it the page's own parts show only once the cover has landed.
 */
@Composable
fun Modifier.mqAboveSharedCovers(zIndexInOverlay: Float = 1f): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    // Only while this screen comes in: a leaving screen's parts stay under the covers and fade with it (#681).
    return with(shared) {
        this@mqAboveSharedCovers.renderInSharedTransitionScopeOverlay(
            renderInOverlay = { isTransitionActive && visibility.isEntering() },
            zIndexInOverlay = zIndexInOverlay,
        )
    }
}

private fun AnimatedVisibilityScope.isEntering(): Boolean = transition.targetState == EnterExitState.Visible

/**
 * The top bar's back button while its screen comes in (owner, #681): drawn above the covers flying in, so from a page
 * with a back button to another it seems to stay in place, and from one without it shows from the start.
 */
@Composable
fun Modifier.mqBackButtonAboveCovers(): Modifier =
    // Not a shared element: a matched one hid itself once its short flight ended, while a cover still flew over it.
    // The entering screen's button drawn on top from the start reads the same, the leaving one fading under it.
    mqAboveSharedCovers(zIndexInOverlay = 2f)

/** The shared-element key of a cover, the same on every screen showing that item. */
fun sharedCoverKey(
    itemId: Any,
    /** The card it was opened from when the same item shows twice (#418: Home sections); `null` for the only one. */
    origin: String? = null,
): String = if (origin == null) "cover-$itemId" else "cover-$itemId@$origin"
