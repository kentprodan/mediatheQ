package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Onboarding start: enter the server address. */
@Serializable
data object OnboardingServerKey : NavKey

/** Username + password login on a checked server. */
@Serializable
data class OnboardingLoginKey(
    val baseUrl: String,
    val serverName: String,
) : NavKey

/** Quick Connect login on a checked server. */
@Serializable
data class OnboardingQuickConnectKey(
    val baseUrl: String,
    val serverName: String,
) : NavKey
