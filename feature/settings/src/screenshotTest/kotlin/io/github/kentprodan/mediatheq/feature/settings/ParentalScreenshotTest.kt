package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

/** Settings -> Video -> Parental controls (#443) on the main profile, a PIN set and a child limited to 12. */
@PreviewTest
@MqPreviews
@Composable
fun ParentalScreenshot() =
    MediatheqTheme {
        ParentalScreen(
            ParentalState(
                hasPin = true,
                isMain = true,
                mainName = "Cristian",
                profiles =
                    listOf(
                        Account("a", "Home", "Cristian", "http://nas", active = true) to null,
                        Account("b", "Home", "Ana", "http://nas", active = false) to 12,
                    ),
                unlocked = true,
            ),
            {},
            { _, _ -> },
            {},
        )
    }
