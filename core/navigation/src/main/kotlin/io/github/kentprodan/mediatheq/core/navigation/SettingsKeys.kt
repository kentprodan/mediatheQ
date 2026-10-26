package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The Settings screen, opened from Home. */
@Serializable
data object SettingsKey : NavKey

/** Settings -> Audio: bit-perfect USB and the equalizer. */
@Serializable
data object AudioSettingsKey : NavKey

/** Settings -> About -> Open-source licenses (#70). */
@Serializable
data object LicensesKey : NavKey
