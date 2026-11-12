package io.github.kentprodan.mediatheq.feature.home

import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings

/** One Home section's settings page (#182). */
sealed interface HomeSectionSettingsUiState {
    data object Loading : HomeSectionSettingsUiState

    /** [preview] is the section as Home shows it now; [copyFrom] the other sections of the same kind. */
    data class Content(
        val preview: HomeSectionContent,
        val copyFrom: List<HomeSection>,
    ) : HomeSectionSettingsUiState {
        val section: HomeSection get() = preview.section
        val settings: HomeSectionSettings get() = preview.settings
    }
}

sealed interface HomeSectionSettingsAction {
    data class Set(
        val settings: HomeSectionSettings,
    ) : HomeSectionSettingsAction

    /** Takes [from]'s settings (a section of the same kind). */
    data class CopyFrom(
        val from: HomeSection,
    ) : HomeSectionSettingsAction

    /** These settings for every section of the same kind. */
    data object ApplyToAll : HomeSectionSettingsAction

    /** This section back to its defaults. */
    data object Restore : HomeSectionSettingsAction
}
