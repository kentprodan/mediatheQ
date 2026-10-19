package io.github.kentprodan.mediatheq.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** The app shell's shared-transition scope; `null` outside the shell (previews, tests). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The current screen's enter/exit animation scope; `null` outside a navigation entry. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Makes a cover fly between screens: two covers with the same [key] (e.g. `"cover-<MediaId>"`) on the leaving and the
 * entering screen animate into each other, using the theme's slow spatial motion (expressive-design skill, section 4).
 * Does nothing where there is no shell or no screen transition, so previews and tests render normally.
 */
@Composable
fun Modifier.mqSharedCover(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    return with(shared) {
        this@mqSharedCover.sharedElement(
            rememberSharedContentState(key),
            visibility,
            boundsTransform = BoundsTransform { _, _ -> spec },
        )
    }
}

/** The shared-element key of a cover, the same on every screen showing that item. */
fun sharedCoverKey(itemId: Any): String = "cover-$itemId"
