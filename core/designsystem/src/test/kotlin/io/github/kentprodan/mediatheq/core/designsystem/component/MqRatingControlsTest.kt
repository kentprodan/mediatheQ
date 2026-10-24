package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MqRatingControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stars(initial: Int?): List<Int?> {
        val rated = mutableListOf<Int?>()
        composeRule.setContent {
            var stars by remember { mutableStateOf(initial) }
            MediatheqTheme {
                MqStarRating(stars, onRate = {
                    rated += it
                    stars = it
                })
            }
        }
        return rated
    }

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `tapping a star rates with it`() {
        val rated = stars(null)

        composeRule.onNodeWithContentDescription("Rate 4 stars").performClick()

        assertThat(rated).containsExactly(4)
    }

    @Test
    fun `tapping the current star clears the rating`() {
        val rated = stars(3)

        composeRule.onNodeWithContentDescription("Clear rating").performClick()

        assertThat(rated).containsExactly(null)
        composeRule.onNode(hasState("Not rated")).assertExists()
    }

    @Test
    fun `the rating is read as a state`() {
        stars(1)

        composeRule.onNode(hasState("1 of 5 stars")).assertExists()
        composeRule.onNodeWithContentDescription("Rate 2 stars").assertExists()
    }

    @Test
    fun `the heart toggles and reports its state`() {
        val toggled = mutableListOf<Boolean>()
        composeRule.setContent {
            var favorite by remember { mutableStateOf(false) }
            MediatheqTheme {
                MqFavoriteToggle(favorite, onToggle = {
                    toggled += it
                    favorite = it
                })
            }
        }

        composeRule.onNodeWithContentDescription("Favorite").assertIsOff().performClick()

        composeRule.onNodeWithContentDescription("Favorite").assertIsOn()
        assertThat(toggled).containsExactly(true)
    }
}
