package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ContactSupportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private var dismissed = 0
    private val info = AboutInfo("1.2.3", 42, "17", "Google Pixel 9")

    private fun show() =
        composeRule.setContent {
            val uris =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                }
            CompositionLocalProvider(LocalUriHandler provides uris) {
                MediatheqTheme { ContactSupportDialog(info) { dismissed++ } }
            }
        }

    @Test
    fun `a feature request opens its template on GitHub and closes the dialog`() {
        show()

        composeRule.onNodeWithText("Make a feature request").performClick()

        assertThat(opened).containsExactly(supportIssueUrl(SupportKind.FeatureRequest, info))
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `an issue report opens the bug template, Cancel opens nothing`() {
        show()

        composeRule.onNodeWithText("Report an issue").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(opened).containsExactly(supportIssueUrl(SupportKind.Issue, info))
        assertThat(dismissed).isEqualTo(2)
    }
}
