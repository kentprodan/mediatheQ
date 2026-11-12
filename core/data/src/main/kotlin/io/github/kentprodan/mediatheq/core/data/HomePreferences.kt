package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings

/**
 * Settings -> Interface -> Page settings -> Home page (#127): which sections show, in what order, and how each looks
 * (its gear, #182).
 */
data class HomePreferences(
    /** Every section, in the order Home shows them. */
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = HomeSection.entries.filterNot { it.shownByDefault }.toSet(),
    /** The sections whose settings were changed; the others use their [HomeSection.defaultSettings]. */
    val settings: Map<HomeSection, HomeSectionSettings> = emptyMap(),
) {
    /** The sections Home shows, in order. */
    val shown: List<HomeSection> get() = order.filterNot { it in hidden }

    /** How [section] looks. */
    fun settingsOf(section: HomeSection): HomeSectionSettings = settings[section] ?: section.defaultSettings
}
