package io.github.kentprodan.mediatheq.feature.tv.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvAccentTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A 1080p TV with a plain Application: no image loader, so a logo never loads here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvTitleAndMenuTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val logo = Artwork("m1", "l1", type = ImageType.Logo)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `Name only shows the name as the heading, with no logo (#523)`() {
        composeRule.setContent {
            MediatheqTvTheme { MqTvTitle("Blade Runner", logo, TvTitlePage.Movie, style = TvTitleStyle.Text) }
        }

        composeRule.onNodeWithText("Blade Runner").assert(heading)
    }

    @Test
    fun `without a logo, Logo shows the name (#523)`() {
        composeRule.setContent {
            MediatheqTvTheme { MqTvTitle("Blade Runner", null, TvTitlePage.Movie, style = TvTitleStyle.Logo) }
        }

        composeRule.onNodeWithText("Blade Runner").assert(heading)
    }

    @Test
    fun `a logo that cannot load gives way to the name, read as the heading (#523)`() {
        composeRule.setContent {
            MediatheqTvTheme { MqTvTitle("Blade Runner", logo, TvTitlePage.Movie, style = TvTitleStyle.Logo) }
        }
        // The failed load arrives from the image loader's own thread.
        composeRule.waitUntil(
            5_000,
        ) { composeRule.onAllNodesWithText("Blade Runner").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("Blade Runner").assert(heading)
    }

    @Test
    fun `a menu with an action and choices opens on its first entry (#523)`() {
        composeRule.setContent {
            MediatheqTvTheme {
                MqTvMenu(
                    "Row",
                    listOf(
                        MqTvMenuEntry("Move up", null) {},
                        MqTvMenuEntry("Classic", null, false) {},
                        MqTvMenuEntry("Compact", null, true) {},
                    ),
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Move up").assertIsFocused()
    }

    @Test
    fun `the poster's accent arriving keeps the page, its focus and its state (#517)`() {
        var seed by mutableStateOf<Int?>(null)
        var created = 0
        composeRule.setContent {
            MediatheqTvTheme {
                MqTvAccentTheme(seed) {
                    remember { created++ }
                    MqTvButton("Play", {})
                }
            }
        }
        composeRule.onNodeWithText("Play").performSemanticsAction(SemanticsActions.RequestFocus)

        seed = 0xFFD9822B.toInt()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Play").assertIsFocused()
        assertThat(created).isEqualTo(1)
    }
}
