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

/** On the Streamer: the playback service starts in the TV app, its controller connects and its session opens TvActivity (#235, device test). */
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
            try {
                assertThat(controller.isConnected).isTrue()
                // The launcher's Now Playing card opens the session activity: TvActivity on TV.
                assertThat(controller.sessionActivity).isNotNull()
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                assertThat(launch?.component?.className).isEqualTo(TvActivity::class.java.name)
            } finally {
                controller.release()
            }
        }
    }
}
