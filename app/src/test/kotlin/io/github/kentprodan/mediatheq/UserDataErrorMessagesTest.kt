package io.github.kentprodan.mediatheq

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UserDataErrorMessagesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a refused change shows the message and keeps it after the error is cleared`() {
        val errors = MutableStateFlow<MqError?>(null)
        composeRule.setContent {
            MediatheqTheme {
                val snackbar = remember { SnackbarHostState() }
                UserDataErrorMessages(errors, { errors.value = null }, snackbar)
                SnackbarHost(snackbar)
            }
        }

        errors.value = MqError.Network
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithText("Could not save that change. Try again.").assertExists()
        assertThat(errors.value).isNull()
    }
}
