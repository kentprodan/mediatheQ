package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Plain Application: the placeholder has no Hilt graph. A 1080p TV.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TvActivity>()

    @Test
    fun `the placeholder opens with focus on its first card, and the D-pad moves it`() {
        composeRule.onNodeWithText("mediatheQ").assertExists()
        composeRule.onNodeWithText("Home").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Library").assertIsFocused()
    }
}
