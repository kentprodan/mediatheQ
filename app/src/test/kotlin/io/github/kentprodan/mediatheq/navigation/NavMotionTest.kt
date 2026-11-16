package io.github.kentprodan.mediatheq.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #405: forward comes from the end, back from the start, a tab switch does not move. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavMotionTest {
    @get:Rule
    val rule = createComposeRule()

    private val motion = NavMotion(tween<IntOffset>(DURATION), tween(DURATION), rtl = false)

    /** Moves from screen 1 to screen 2 with [transform] and returns screen 2's left edge halfway through. */
    private fun halfway(transform: NavMotion.() -> androidx.compose.animation.ContentTransform): Float {
        var screen by mutableIntStateOf(1)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AnimatedContent(screen, transitionSpec = { motion.transform() }, label = "nav") { shown ->
                Box(Modifier.fillMaxSize()) { Text("screen $shown") }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        Snapshot.withMutableSnapshot { screen = 2 }
        // Frames to recompose and start the transition, then half of it.
        rule.mainClock.advanceTimeBy(FRAMES)
        rule.mainClock.advanceTimeBy(DURATION / 2L)
        return rule
            .onNodeWithText("screen 2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .left.value
    }

    @Test
    fun `forward slides the new screen in from the end`() {
        assertThat(halfway { forward(tabSwitch = false) }).isGreaterThan(0f)
    }

    @Test
    fun `back slides the screen below in from the start`() {
        assertThat(halfway { back(tabSwitch = false) }).isLessThan(0f)
    }

    @Test
    fun `a tab switch only fades`() {
        assertThat(halfway { forward(tabSwitch = true) }).isEqualTo(0.dp.value)
    }

    private companion object {
        const val DURATION = 400
        const val FRAMES = 32L
    }
}
