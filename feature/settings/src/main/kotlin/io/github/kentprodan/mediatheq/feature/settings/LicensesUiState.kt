package io.github.kentprodan.mediatheq.feature.settings

/** One entry of the open-source licenses (#70): the app, a copied asset or a library it ships. */
data class LicenseEntry(
    val id: String,
    val name: String,
    /** The version, or what the entry is ("Icons"); `null` when unknown. */
    val detail: String?,
    val licenseName: String,
    /** The license's full text; `null` when there is none (a trademark). */
    val licenseText: String?,
    /** The project's page, or the source for the app itself. */
    val url: String?,
    /** The library's NOTICE file, shown with its license (Apache-2.0 section 4(d), #87); most have none. */
    val notice: String? = null,
)

/** What the licenses screen shows. */
sealed interface LicensesUiState {
    /** The build's report is being read (a few milliseconds). */
    data object Loading : LicensesUiState

    /** Every entry; [openId] is the one whose license text shows. */
    data class Content(
        val entries: List<LicenseEntry>,
        val openId: String? = null,
    ) : LicensesUiState
}

sealed interface LicensesAction {
    /** Shows the license of [id], or hides it when it is the open one. */
    data class Toggle(
        val id: String,
    ) : LicensesAction
}
