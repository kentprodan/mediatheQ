package io.github.kentprodan.mediatheq.feature.video

import android.app.Activity
import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VideoPipButtonTest {
    @Test
    fun `the PiP button puts the activity in picture in picture (#445)`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        enterPip(activity)

        assertThat(activity.isInPictureInPictureMode).isTrue()
    }
}
