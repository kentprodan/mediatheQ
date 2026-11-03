package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.HomeSection

/** Settings -> Interface -> Page settings -> Home page (#127): which sections show, in what order. */
data class HomePreferences(
    /** Every section, in the order Home shows them. */
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = HomeSection.entries.filterNot { it.shownByDefault }.toSet(),
) {
    /** The sections Home shows, in order. */
    val shown: List<HomeSection> get() = order.filterNot { it in hidden }
}
