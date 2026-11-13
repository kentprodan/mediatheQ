package io.github.kentprodan.mediatheq.feature.tv.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** An onboarding step on the TV: a heading, then [content] in a column narrow enough to read from the couch. */
@Composable
internal fun TvOnboardingLayout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize()) {
        // A Box lets the column be narrower than the screen (the Surface makes its child fill it).
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = 96.dp, vertical = 64.dp).widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                content()
            }
        }
    }
}

/** A line of text in the error color. */
@Composable
internal fun TvOnboardingError(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
