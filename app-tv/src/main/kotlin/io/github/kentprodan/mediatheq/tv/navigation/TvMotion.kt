package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * How TV pages change (#444): Standard fades with a slight scale (a cover flight shows little across a room), Simple
 * is a plain fast fade (TV Settings -> Transitions).
 */
internal object TvMotion {
    fun forward(simple: Boolean): ContentTransform =
        if (simple) {
            plain()
        } else {
            (fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), initialScale = SCALE)) togetherWith
                fadeOut(tween(EXIT_MS))
        }

    fun back(simple: Boolean): ContentTransform =
        if (simple) {
            plain()
        } else {
            fadeIn(tween(ENTER_MS)) togetherWith
                (fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = SCALE))
        }

    private fun plain(): ContentTransform = fadeIn(tween(SIMPLE_MS)) togetherWith fadeOut(tween(SIMPLE_MS))

    private const val ENTER_MS = 220
    private const val EXIT_MS = 150
    private const val SIMPLE_MS = 90
    private const val SCALE = 0.96f
}
