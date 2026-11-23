package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

/**
 * The guidelines' side panel (#579): a full-height panel that slides in from the right over the dimmed screen, for
 * Sort, Filter and their choices. [title] heads the list; the item given the requester takes focus when the panel opens
 * and again whenever [page] changes, so one panel can show several pages without closing (#570: a new dialog per page
 * flickered). Back closes it ([onDismiss]).
 */
@Composable
fun MqTvSidePanel(
    title: String,
    onDismiss: () -> Unit,
    page: Any? = null,
    content: LazyListScope.(FocusRequester) -> Unit,
) {
    val focused = remember { FocusRequester() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                shown,
                enter = slideInHorizontally { it },
            ) { MqTvSidePanelContent(title) { content(focused) } }
        }
    }
    LaunchedEffect(page) {
        // The page's items compose during the dialog window's next layout.
        withFrameNanos {}
        runCatching { focused.requestFocus() }
    }
}

/** The panel itself, without its window: the screenshot test draws it (previews render no dialog). */
@Composable
internal fun MqTvSidePanelContent(
    title: String,
    content: LazyListScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxHeight().width(PANEL_WIDTH),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(top = 48.dp)) {
            Text(
                title,
                Modifier.padding(horizontal = 36.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

/** The guidelines' side panel width. */
private val PANEL_WIDTH = 400.dp
