package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

/**
 * TV tabs (#237, #522, Google TV guidelines): pill tabs on top, focusing a tab shows it and its content slides in from
 * the side it came from. Each tab keeps its own scroll and focus while another shows; Up from the content lands on the
 * selected tab (never switching tabs by itself), Back from the content goes up to the tabs first (#348).
 *
 * [content] gets the tab and `cameBack`: true while the screen just opened (back from a detail page), so the tab's last
 * card can take focus; false after a tab switch, which leaves focus on the tabs.
 */
@Composable
fun <T> MqTvTabs(
    tabs: List<T>,
    label: @Composable (T) -> String,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    /** The tab shown when the screen first opens. */
    initial: Int = 0,
    content: @Composable (tab: T, cameBack: Boolean) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(initial) }
    val index = selected.coerceIn(tabs.indices)
    var cameBack by remember { mutableStateOf(true) }
    val states = rememberSaveableStateHolder()
    val selectedTab = remember { FocusRequester() }
    var inContent by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        TabRow(index, Modifier.padding(start = TAB_EDGE, top = 24.dp).focusRestorer(selectedTab)) {
            tabs.forEachIndexed { i, tab ->
                Tab(
                    selected = i == index,
                    onFocus = {
                        if (i != index) {
                            selected = i
                            cameBack = false
                        }
                    },
                    modifier = if (i == index) Modifier.focusRequester(selectedTab) else Modifier,
                    // The selected tab's pill is dark while the row has no focus: light text on it.
                    colors =
                        TabDefaults.pillIndicatorTabColors(
                            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Text(
                        label(tab),
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        AnimatedContent(
            index,
            Modifier.onFocusChanged { inContent = it.hasFocus }.focusGroup(),
            transitionSpec = {
                // A short slide of a fraction of the width, from the side of the newly picked tab.
                val toRight = targetState > initialState
                (
                    slideInHorizontally(tween(SLIDE_MILLIS)) { if (toRight) it / 8 else -it / 8 } +
                        fadeIn(tween(SLIDE_MILLIS))
                ).togetherWith(
                    slideOutHorizontally(tween(SLIDE_MILLIS)) { if (toRight) -it / 8 else it / 8 } +
                        fadeOut(tween(SLIDE_MILLIS / 2)),
                )
            },
            label = "tabs",
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                states.SaveableStateProvider(key(tabs[shown])) { content(tabs[shown], cameBack) }
            }
        }
    }
    BackHandler(enabled = inContent) { runCatching { selectedTab.requestFocus() } }
}

private const val SLIDE_MILLIS = 200

// The TV safe area at the sides (48 dp, as every TV screen).
private val TAB_EDGE = 48.dp
