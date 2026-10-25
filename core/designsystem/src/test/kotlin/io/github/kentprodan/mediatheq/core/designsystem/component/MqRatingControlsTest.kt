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

    /** Shows the stars at [initial] (half stars) and returns what each tap rates. */
    private fun stars(
        initial: Int?,
        allowHalf: Boolean = false,
    ): List<Int?> {
        val rated = mutableListOf<Int?>()
        composeRule.setContent {
            var halfStars by remember { mutableStateOf(initial) }
            MediatheqTheme {
                MqStarRating(halfStars, onRate = {
                    rated += it
                    halfStars = it
                }, allowHalf = allowHalf)
            }
        }
        return rated
    }

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `tapping a star rates with it`() {
        val rated = stars(null)

        composeRule.onNodeWithContentDescription("Rate 4 stars").performClick()

        assertThat(rated).containsExactly(8)
    }

    @Test
    fun `tapping the current star clears the rating`() {
        val rated = stars(6)

        composeRule.onNodeWithContentDescription("Clear rating").performClick()

        assertThat(rated).containsExactly(null)
        composeRule.onNode(hasState("Not rated")).assertExists()
    }

    @Test
    fun `the rating is read as a state`() {
        stars(2)

        composeRule.onNode(hasState("1 of 5 stars")).assertExists()
        composeRule.onNodeWithContentDescription("Rate 2 stars").assertExists()
    }

    @Test
    fun `with half stars, tapping the current star makes it half, then clears it`() {
        val rated = stars(null, allowHalf = true)

        repeat(3) { composeRule.onNodeWithContentDescription(nextLabel(it)).performClick() }

        assertThat(rated).containsExactly(6, 5, null).inOrder()
        composeRule.onNode(hasState("Not rated")).assertExists()
    }

    private fun nextLabel(tap: Int) = listOf("Rate 3 stars", "Rate 2.5 stars", "Clear rating")[tap]

    @Test
    fun `a half star rating is read as such`() {
        stars(7, allowHalf = true)

        composeRule.onNode(hasState("3.5 of 5 stars")).assertExists()
    }

    @Test
    fun `whole stars show a half star rating set elsewhere rounded`() {
        stars(7)

        composeRule.onNode(hasState("4 of 5 stars")).assertExists()
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
