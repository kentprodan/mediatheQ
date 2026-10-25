package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

/** What Settings and its Audio page show. */
sealed interface SettingsUiState {
    /** Until the account and the preferences are read. */
    data object Loading : SettingsUiState

    data class Content(
        val serverName: String,
        val userName: String,
        val audio: AudioPreferences = AudioPreferences(),
        val library: LibraryPreferences = LibraryPreferences(),
        /** What the output does with the audio settings (bit-perfect, why the equalizer is unavailable). */
        val output: AudioOutputInfo = AudioOutputInfo(),
    ) : SettingsUiState
}

sealed interface SettingsAction {
    data object LogOut : SettingsAction

    /** The quality on [metered] networks (mobile data), or on unmetered ones (Wi-Fi). */
    data class SetStreamingQuality(
        val metered: Boolean,
        val quality: StreamingQuality,
    ) : SettingsAction

    data class SetNormalization(
        val normalization: Normalization,
    ) : SettingsAction

    data class SetStreamingCacheGb(
        val gb: Int,
    ) : SettingsAction

    data class SetDownloadsUnmeteredOnly(
        val on: Boolean,
    ) : SettingsAction

    data class SetDynamicColor(
        val on: Boolean,
    ) : SettingsAction

    data class SetAlbumColumns(
        val columns: Int,
    ) : SettingsAction

    data class SetRatingMode(
        val mode: RatingMode,
    ) : SettingsAction

    data class SetBitPerfectUsb(
        val on: Boolean,
    ) : SettingsAction

    data class SetEqualizerOn(
        val on: Boolean,
    ) : SettingsAction

    data class SetEqPreset(
        val preset: EqPreset,
    ) : SettingsAction

    /** Set equalizer band [index] to [gainDb]. */
    data class SetEqBand(
        val index: Int,
        val gainDb: Float,
    ) : SettingsAction
}
