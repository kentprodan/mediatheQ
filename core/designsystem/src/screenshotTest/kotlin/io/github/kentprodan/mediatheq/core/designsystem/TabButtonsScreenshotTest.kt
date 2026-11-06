package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTabButtons
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

/** Connected tab buttons (#140): two tabs with the first, then the second selected; three with the middle one. */
@PreviewTest
@MqPreviews
@Composable
fun TabButtonsScreenshot() {
    MediatheqTheme {
        Surface {
            Column(Modifier.padding(MediatheqSpacing.l)) {
                MqTabButtons(listOf("Settings", "Equalizer"), selected = 0, onSelect = {})
                MqTabButtons(listOf("Settings", "Equalizer"), selected = 1, onSelect = {})
                MqTabButtons(listOf("Day", "Week", "Month"), selected = 1, onSelect = {})
            }
        }
    }
}
