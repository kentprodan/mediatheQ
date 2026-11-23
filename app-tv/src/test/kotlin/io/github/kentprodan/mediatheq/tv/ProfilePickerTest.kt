package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProfilePickerTest {
    private var now = Instant.fromEpochSeconds(1_000_000)
    private val clock =
        object : Clock {
            override fun now() = now
        }

    @Test
    fun `the picker is due after 4 hours away or on the first start, never after a quick return`() {
        assertThat(profilePickerDue(now - 4.hours, now)).isTrue()
        assertThat(profilePickerDue(null, now)).isTrue()
        assertThat(profilePickerDue(now - 3.hours - 59.minutes, now)).isFalse()
        assertThat(profilePickerDue(now - 2.minutes, now)).isFalse()
        assertThat(profilePickerDue(now - 2.minutes, now, closedWithBack = true)).isTrue()
    }

    @Test
    fun `closing with Back asks at the next start however soon, leaving with Home does not (#572)`() {
        val gate = ProfileGate(ApplicationProvider.getApplicationContext(), clock)
        gate.close()

        // Home: out and back two minutes later.
        gate.onBackground()
        now += 2.minutes
        gate.onForeground()
        assertThat(gate.state.value).isEqualTo(ProfilePicker.Hidden)

        // Back: closed, opened again a minute later.
        gate.closedWithBack()
        gate.onBackground()
        now += 1.minutes
        gate.onForeground()
        assertThat(gate.state.value).isEqualTo(ProfilePicker.Due)

        // Asked once: the next quick return does not ask again.
        gate.close()
        gate.onBackground()
        now += 1.minutes
        gate.onForeground()
        assertThat(gate.state.value).isEqualTo(ProfilePicker.Hidden)
    }
}
