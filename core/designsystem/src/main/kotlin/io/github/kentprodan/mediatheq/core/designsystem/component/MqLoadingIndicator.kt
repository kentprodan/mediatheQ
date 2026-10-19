package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.R
import kotlinx.coroutines.delay

/**
 * Full-screen loading state: the expressive `LoadingIndicator`, centered. It appears only after [delayMillis],
 * so fast loads never flash it (expressive-design skill, section 3).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = DEFAULT_DELAY_MILLIS,
) {
    var visible by remember(delayMillis) { mutableStateOf(delayMillis <= 0) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis)
        visible = true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) {
            val description = stringResource(R.string.mq_loading)
            LoadingIndicator(Modifier.semantics { contentDescription = description })
        }
    }
}

private const val DEFAULT_DELAY_MILLIS = 300L
