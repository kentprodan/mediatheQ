package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** The title above a TV row (#236), a heading for accessibility. */
@Composable
fun MqTvRowHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
