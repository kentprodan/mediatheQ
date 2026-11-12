package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AboutScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private val info = AboutInfo("1.2.3", 42, "17", "Google Pixel 9")

    private fun show(showcase: Boolean) =
        composeRule.setContent {
            MediatheqTheme {
                AboutScreen(
                    info,
                    onCopyVersion = { events += "copy" },
                    onOpenChangelog = { events += "changelog" },
                    onOpenLicenses = { events += "licenses" },
                    onOpenSource = { events += "source" },
                    onOpenPrivacy = { events += "privacy" },
                    onOpenShowcase = { events += "showcase" }.takeIf { showcase },
                    onBack = { events += "back" },
                )
            }
        }

    private fun row(title: String) = hasText(title) and hasClickAction()

    private fun open(title: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(row(title))
        composeRule.onNode(row(title)).performClick()
    }

    @Test
    fun `every row opens its page, the version copies on a tap or a long press`() {
        show(showcase = true)

        composeRule.onNodeWithText("1.2.3 (42)").assertExists()
        listOf(
            "Version",
            "Changelog",
            "Open-source licenses",
            "Source code",
            "Privacy",
            "Theme showcase",
        ).forEach(::open)
        composeRule.onNode(row("Version")).performTouchInput { longClick() }

        assertThat(events)
            .containsExactly("copy", "changelog", "licenses", "source", "privacy", "showcase", "copy")
            .inOrder()
    }

    @Test
    fun `the long press says what it does`() {
        show(showcase = false)

        composeRule
            .onNode(row("Version"))
            .assert(
                SemanticsMatcher("long click labelled Copy version") {
                    it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Copy version"
                },
            )
    }

    @Test
    fun `release builds have no theme showcase`() {
        show(showcase = false)

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(row("Privacy"))
        composeRule.onNodeWithText("Theme showcase").assertDoesNotExist()
    }
}
