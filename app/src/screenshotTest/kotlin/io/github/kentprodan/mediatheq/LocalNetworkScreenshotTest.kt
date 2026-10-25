package io.github.kentprodan.mediatheq

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

@PreviewTest
@MqPreviews
@Composable
fun LocalNetworkRequiredScreenshot() = MediatheqTheme { LocalNetworkRequired(false, {}, {}, {}) }

@PreviewTest
@MqPreviews
@Composable
fun LocalNetworkDeniedScreenshot() = MediatheqTheme { LocalNetworkRequired(true, {}, {}, {}) }
