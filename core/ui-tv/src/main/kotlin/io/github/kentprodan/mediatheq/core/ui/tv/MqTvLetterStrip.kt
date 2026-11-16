package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** The A-Z strip (#237): one round key per letter; selecting one jumps to the letter's first card and focuses it. */
@Composable
fun MqTvLetterStrip(
    letters: List<Letter>,
    onJump: (Letter) -> Unit,
) {
    LazyColumn(
        // Back to the strip: the letter used last.
        Modifier.width(56.dp).fillMaxHeight().focusRestorer(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(letters, key = { it.letter }) { letter ->
            Surface(
                onClick = { onJump(letter) },
                modifier = Modifier.size(32.dp),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(letter.letter.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
