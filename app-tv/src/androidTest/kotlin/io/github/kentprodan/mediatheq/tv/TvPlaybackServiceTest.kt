package io.github.kentprodan.mediatheq.tv

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** On the Streamer: the playback service starts in the TV app and its controller connects (#235, device test). */
@RunWith(AndroidJUnit4::class)
class TvPlaybackServiceTest {
    @Test
    fun serviceStartsAndControllerConnects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token =
            SessionToken(
                context,
                ComponentName(context, "io.github.kentprodan.mediatheq.core.playback.MediatheqPlaybackService"),
            )

        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThat(controller.isConnected).isTrue()
            controller.release()
        }
    }
}
