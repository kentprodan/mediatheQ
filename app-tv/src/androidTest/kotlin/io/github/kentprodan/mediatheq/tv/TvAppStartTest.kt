package io.github.kentprodan.mediatheq.tv

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** On the Streamer (testing skill: device tests): the app starts, and the launch intent is its one activity. */
@RunWith(AndroidJUnit4::class)
class TvAppStartTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theLaunchIntentOpensTvActivity() {
        // The playback service uses it for the session activity (ADR 0038).
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)

        assertThat(launch?.component?.className).isEqualTo(TvActivity::class.java.name)
    }

    @Test
    fun theAppStarts() {
        ActivityScenario.launch(TvActivity::class.java).use { scenario ->
            scenario.onActivity { assertThat(it.isFinishing).isFalse() }
        }
    }
}
