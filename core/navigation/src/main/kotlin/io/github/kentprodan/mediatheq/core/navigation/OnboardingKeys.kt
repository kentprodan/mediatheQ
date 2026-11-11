package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Onboarding start: enter the server address. */
@Serializable
data object OnboardingServerKey : NavKey

/**
 * Adding an account from Settings -> Manage media providers (#147): the server address screen inside the app; a
 * successful sign-in returns to the providers page.
 */
@Serializable
data object AddAccountKey : NavKey

/**
 * Username + password login on a checked server. [returnSteps] > 0 when signing in from inside the app (#147): after
 * success the screen goes back that many steps; 0 in onboarding, where the app switches to the shell instead.
 */
@Serializable
data class OnboardingLoginKey(
    val baseUrl: String,
    val serverName: String,
    val returnSteps: Int = 0,
) : NavKey

/** Quick Connect login on a checked server; [returnSteps] as in [OnboardingLoginKey]. */
@Serializable
data class OnboardingQuickConnectKey(
    val baseUrl: String,
    val serverName: String,
    val returnSteps: Int = 0,
) : NavKey
