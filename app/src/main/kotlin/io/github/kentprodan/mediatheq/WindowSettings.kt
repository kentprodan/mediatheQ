package io.github.kentprodan.mediatheq

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.ScreenOrientation

/** The Activity orientation for [ScreenOrientation]; the user's own landscape / portrait keep both sides (#137). */
internal fun ScreenOrientation.activityOrientation(): Int =
    when (this) {
        ScreenOrientation.System -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

/**
 * Advanced settings that act on the window (#137): orientation and the status bar (hidden, shown by a swipe). Keep
 * screen on is applied by the player host, which shows whenever someone is signed in.
 */
@Composable
internal fun Activity.WindowSettings(advanced: AdvancedPreferences) {
    LaunchedEffect(advanced.orientation) { requestedOrientation = advanced.orientation.activityOrientation() }
    LaunchedEffect(advanced.hideStatusBar) {
        WindowCompat.getInsetsController(window, window.decorView).run {
            if (advanced.hideStatusBar) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}
