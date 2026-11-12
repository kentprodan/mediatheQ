package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes

/**
 * mediatheQ's mark for the About page (#151): a music note in the expressive sunny shape (an accent). Decorative;
 * the app's name is written next to it. Stands in until the launcher icon exists (#264).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqAppMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(MediatheqSizes.appMark)
            .clip(MaterialShapes.Sunny.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MqIcons.MusicNote,
            contentDescription = null,
            Modifier.fillMaxSize(ICON_FRACTION),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val ICON_FRACTION = 0.5f
