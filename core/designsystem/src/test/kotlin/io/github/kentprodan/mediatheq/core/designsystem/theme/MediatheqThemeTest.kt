package io.github.kentprodan.mediatheq.core.designsystem.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediatheqThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val seen = mutableListOf<ColorScheme>()
    private var options by mutableStateOf(MqThemeOptions())

    private fun show() =
        composeRule.setContent {
            MediatheqTheme(options, systemDark = false) {
                seen += MaterialTheme.colorScheme
                // Read, so a font change recomposes this content too.
                MaterialTheme.typography
            }
        }

    private fun changeOptions(new: MqThemeOptions) {
        options = new
        // With the clock paused, the change reaches the recomposer only when announced.
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `a theme at rest hands out the same scheme on every recomposition`() {
        show()

        options = options.copy(font = MqFont.Inter)
        composeRule.waitForIdle()

        assertThat(seen.size).isAtLeast(2)
        // ColorScheme compares by identity: a new copy would restart every blend downstream.
        assertThat(seen.distinct()).containsExactly(IrisLightColorScheme)
    }

    @Test
    fun `a theme change blends, and lands on the new scheme`() {
        show()
        composeRule.mainClock.autoAdvance = false

        changeOptions(options.copy(mode = MqThemeMode.Dark))
        composeRule.mainClock.advanceTimeBy(TWO_FRAMES_MS)
        assertThat(seen.last()).isNotSameInstanceAs(IrisDarkColorScheme)
        assertThat(seen.last()).isNotSameInstanceAs(IrisLightColorScheme)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertThat(seen.last()).isSameInstanceAs(IrisDarkColorScheme)
    }

    @Test
    fun `with instant color updates a theme change applies in one frame`() {
        options = MqThemeOptions(instantColors = true)
        show()
        composeRule.mainClock.autoAdvance = false

        changeOptions(options.copy(mode = MqThemeMode.Dark))
        composeRule.mainClock.advanceTimeBy(TWO_FRAMES_MS)

        assertThat(seen.last()).isSameInstanceAs(IrisDarkColorScheme)
    }
}

// Far shorter than any effects spec.
private const val TWO_FRAMES_MS = 32L
