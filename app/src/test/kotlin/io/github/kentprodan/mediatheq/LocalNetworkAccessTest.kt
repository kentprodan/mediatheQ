package io.github.kentprodan.mediatheq

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalNetworkAccessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun showGate() {
        composeRule.setContent {
            MediatheqTheme { LocalNetworkGate(requiresPermission = true) { Text("Library") } }
        }
    }

    @Test
    fun `without local network access the app asks for it instead of opening`() {
        showGate()

        composeRule.onNodeWithText("Allow access to your home network").assertExists()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
    }

    @Test
    fun `with local network access the app opens`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)

        showGate()

        composeRule.onNodeWithText("Library").assertExists()
    }

    @Test
    fun `not now opens the app without local network access`() {
        showGate()

        composeRule.onNodeWithText("Not now").performClick()

        composeRule.onNodeWithText("Library").assertExists()
    }

    @Test
    fun `refused for good only after the system stopped asking, never for a dismissed dialog`() {
        // The dialog was closed without an answer: no rationale before or after, the system can still ask.
        assertThat(isDeniedForGood(rationaleBefore = false, rationaleAfter = false, silentDenials = 1)).isFalse()
        // Refused once (rationale now true): ask again.
        assertThat(isDeniedForGood(rationaleBefore = false, rationaleAfter = true, silentDenials = 0)).isFalse()
        // Refused again after a rationale: the system will not ask any more.
        assertThat(isDeniedForGood(rationaleBefore = true, rationaleAfter = false, silentDenials = 0)).isTrue()
        // Already refused for good in an earlier session: two requests in a row came back without a dialog.
        assertThat(isDeniedForGood(rationaleBefore = false, rationaleAfter = false, silentDenials = 2)).isTrue()
    }

    @Test
    fun `before Android 17 the gate never shows`() {
        composeRule.setContent {
            MediatheqTheme { LocalNetworkGate(requiresPermission = false) { Text("Library") } }
        }

        composeRule.onNodeWithText("Library").assertExists()
    }

    @Test
    fun `allow asks for the permission`() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            MediatheqTheme {
                LocalNetworkRequired(false, onAllow = {
                    calls += "allow"
                }, onOpenSettings = { calls += "settings" }, onNotNow = {
                    calls +=
                        "later"
                })
            }
        }

        composeRule.onNodeWithText("Allow").performClick()

        assertThat(calls).containsExactly("allow")
    }

    @Test
    fun `after a refusal for good the button opens the settings`() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            MediatheqTheme {
                LocalNetworkRequired(true, onAllow = {
                    calls += "allow"
                }, onOpenSettings = { calls += "settings" }, onNotNow = {
                    calls +=
                        "later"
                })
            }
        }

        composeRule.onNodeWithText("Open settings").performClick()

        assertThat(calls).containsExactly("settings")
    }
}
