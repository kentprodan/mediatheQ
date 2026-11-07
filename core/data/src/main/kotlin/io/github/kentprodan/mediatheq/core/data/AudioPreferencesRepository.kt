package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/** Audio, streaming and storage preferences, kept across restarts. */
interface AudioPreferencesRepository {
    val preferences: Flow<AudioPreferences>

    /** The quality on [metered] networks, or on unmetered ones. */
    suspend fun setStreamingQuality(
        metered: Boolean,
        quality: StreamingQuality,
    )

    /** Ignored outside [AudioPreferences.CACHE_SIZES_GB]. */
    suspend fun setStreamingCacheGb(gb: Int)

    suspend fun setDownloadsUnmeteredOnly(on: Boolean)

    /** Settings -> Playback -> Decoding and transcoding (#138). */
    suspend fun setForceInstantTranscoding(on: Boolean)

    /** Back to the Decoding and transcoding defaults only: both qualities and instant transcoding. */
    suspend fun restoreDecodingDefaults()

    /** One Playback behavior switch (#138); the others keep their stored values. */
    suspend fun setBehavior(
        switch: BehaviorSwitch,
        on: Boolean,
    )

    /** Back to the Playback behavior defaults only. */
    suspend fun restoreBehaviorDefaults()

    /** Every Audio focus value at once (#139); `null` = that page's defaults. */
    suspend fun setAudioFocus(audioFocus: AudioFocusPreferences?)

    /** Every Headset buttons value at once (#139); `null` = that page's defaults. */
    suspend fun setHeadset(headset: HeadsetPreferences?)

    /** Every Notification buttons value at once (#139); `null` = that page's defaults. */
    suspend fun setNotificationButtons(buttons: NotificationButtonsPreferences?)
}

/** [gains] with band [index] set to [gainDb], clamped; `null` for an index outside the bands. */
fun withEqBand(
    gains: List<Float>,
    index: Int,
    gainDb: Float,
): List<Float>? =
    if (index !in gains.indices) {
        null
    } else {
        gains.toMutableList().apply { this[index] = gainDb.coerceIn(AudioPreferences.EQ_GAIN_DB) }
    }
