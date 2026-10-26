package io.github.kentprodan.mediatheq

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import io.github.kentprodan.mediatheq.core.playback.PlaybackError.Reason
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaybackErrorMessagesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val errors = MutableStateFlow<PlaybackError?>(null)

    private fun show(error: PlaybackError) {
        composeRule.setContent {
            MediatheqTheme {
                val snackbar = remember { SnackbarHostState() }
                PlaybackErrorMessages(errors, { errors.value = null }, snackbar)
                SnackbarHost(snackbar)
            }
        }
        errors.value = error
        composeRule.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `a skipped track that is not downloaded says so, and the error is cleared`() {
        show(PlaybackError("Strobe", Reason.NotDownloaded, skipped = true))

        composeRule.onNodeWithText("“Strobe” is not downloaded, skipped").assertExists()
        assertThat(errors.value).isNull()
    }

    @Test
    fun `a track that stopped playback says it could not play`() {
        show(PlaybackError("Strobe", Reason.Failed, skipped = false))

        composeRule.onNodeWithText("“Strobe” could not play").assertExists()
    }
}
