package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/**
 * A user's round picture (#556): the server's user image over the name's initial, so the initial shows while the
 * image loads and when the user has none (Jellyfin answers 404, Coil draws nothing).
 */
@Composable
fun MqTvAvatar(
    name: String,
    imageUrl: String?,
    size: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = textStyle, color = MaterialTheme.colorScheme.onSecondaryContainer)
        imageUrl?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}
