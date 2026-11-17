package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

/** Settings -> Home page -> Genre rows (#441). */
@PreviewTest
@MqPreviews
@Composable
fun HomeGenresScreenshot() =
    MediatheqTheme {
        HomeGenresScreen(
            listOf(
                HomeGenre("Drama", 42, shown = true),
                HomeGenre("Comedy", 17, shown = false),
                HomeGenre("Crime", 1, shown = true),
            ),
            {},
            {},
        )
    }
