package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes

/**
 * mediatheQ's mark for the About page (#151): the launcher icon's layers (#264) in the expressive sunny shape.
 * Decorative; the app's name is written next to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqAppMark(modifier: Modifier = Modifier) {
    Box(modifier.size(MediatheqSizes.appMark).clip(MaterialShapes.Sunny.toShape())) {
        Image(
            painterResource(R.drawable.mq_app_icon_background),
            contentDescription = null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Image(painterResource(R.drawable.mq_app_icon_foreground), contentDescription = null, Modifier.fillMaxSize())
    }
}
