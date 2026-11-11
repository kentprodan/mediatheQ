package io.github.kentprodan.mediatheq.core.data

/** Equalizer presets: gains in dB for the bands of [AudioPreferences.EQ_BANDS_HZ]. [Custom] uses the user's own. */
enum class EqPreset(
    val gainsDb: List<Float>?,
) {
    Flat(listOf(0f, 0f, 0f, 0f, 0f)),
    BassBoost(listOf(6f, 4f, 0f, 0f, 0f)),
    TrebleBoost(listOf(0f, 0f, 0f, 4f, 6f)),
    Vocal(listOf(-2f, 0f, 4f, 3f, 0f)),
    Custom(null),
}

/** Which ReplayGain-style gain sets the volume of a track (media3 skill, section 7). */
enum class Normalization { Album, Track, Off }

/** Streaming quality on one kind of network: the original file, or Opus at [bitrate] bits per second. */
enum class StreamingQuality(
    val bitrate: Int?,
) {
    Original(null),
    Kbps320(320_000),
    Kbps256(256_000),
    Kbps192(192_000),
    Kbps128(128_000),
}

/** One switch of [PlaybackBehavior], so a change never writes the others back from an older copy. */
enum class BehaviorSwitch {
    PreservePlayNextOrder,
    RewindFirst,
    Circular,
    KeepPausedOnSkip,
    StopRepeatOneOnSkip,
    RetainPlayerState,
    WeightedShuffle,
    BluetoothLyrics,
}

/**
 * Settings -> Playback -> Playback (#138): how the queue and the transport buttons behave, everywhere alike, and
 * what Bluetooth displays show (#143).
 */
data class PlaybackBehavior(
    /** Play next entries go after earlier Play next entries, not right after the current track. */
    val preservePlayNextOrder: Boolean = false,
    /** Previous restarts the track unless it is near its start (3 s). */
    val rewindFirst: Boolean = true,
    /** Previous on the first track goes to the last, next on the last to the first. */
    val circular: Boolean = true,
    /** Next / previous while paused stay paused; off, a skip starts playing. */
    val keepPausedOnSkip: Boolean = false,
    /** A manual skip while repeat one is on switches to repeat all. */
    val stopRepeatOneOnSkip: Boolean = true,
    /** Shuffle and repeat carry over to a new queue; off, a new queue starts with repeat off. */
    val retainPlayerState: Boolean = false,
    /** Shuffle prefers tracks not played recently, favorites and higher ratings; off, uniform random. */
    val weightedShuffle: Boolean = true,
    /** Bluetooth displays show the current synced lyrics line as the title (#143, ADR 0034). */
    val bluetoothLyrics: Boolean = false,
) {
    fun isOn(switch: BehaviorSwitch): Boolean =
        when (switch) {
            BehaviorSwitch.PreservePlayNextOrder -> preservePlayNextOrder
            BehaviorSwitch.RewindFirst -> rewindFirst
            BehaviorSwitch.Circular -> circular
            BehaviorSwitch.KeepPausedOnSkip -> keepPausedOnSkip
            BehaviorSwitch.StopRepeatOneOnSkip -> stopRepeatOneOnSkip
            BehaviorSwitch.RetainPlayerState -> retainPlayerState
            BehaviorSwitch.WeightedShuffle -> weightedShuffle
            BehaviorSwitch.BluetoothLyrics -> bluetoothLyrics
        }

    fun with(
        switch: BehaviorSwitch,
        on: Boolean,
    ): PlaybackBehavior =
        when (switch) {
            BehaviorSwitch.PreservePlayNextOrder -> copy(preservePlayNextOrder = on)
            BehaviorSwitch.RewindFirst -> copy(rewindFirst = on)
            BehaviorSwitch.Circular -> copy(circular = on)
            BehaviorSwitch.KeepPausedOnSkip -> copy(keepPausedOnSkip = on)
            BehaviorSwitch.StopRepeatOneOnSkip -> copy(stopRepeatOneOnSkip = on)
            BehaviorSwitch.RetainPlayerState -> copy(retainPlayerState = on)
            BehaviorSwitch.WeightedShuffle -> copy(weightedShuffle = on)
            BehaviorSwitch.BluetoothLyrics -> copy(bluetoothLyrics = on)
        }
}

/**
 * Validated streaming, storage and playback preferences (media3 skill, sections 3, 9 and 10). Equalizer,
 * normalization and bit-perfect are per output: [OutputProfile] (#140).
 */
data class AudioPreferences(
    /** Quality on unmetered networks (Wi-Fi, Ethernet). */
    val unmeteredQuality: StreamingQuality = StreamingQuality.Original,
    /** Quality on metered networks (mobile data). */
    val meteredQuality: StreamingQuality = StreamingQuality.Kbps256,
    /** The streaming cache size, one of [CACHE_SIZES_GB]. */
    val streamingCacheGb: Int = DEFAULT_CACHE_GB,
    /** Downloads wait for an unmetered network; off = any network. */
    val downloadsUnmeteredOnly: Boolean = true,
    /** New downloads (#144): the original when the device plays it, or always one Opus file at this bitrate. */
    val downloadQuality: StreamingQuality = StreamingQuality.Original,
    /** How many downloads run at once, one of [PARALLEL_DOWNLOADS] (#144). */
    val maxParallelDownloads: Int = DEFAULT_PARALLEL_DOWNLOADS,
    /** Losing Wi-Fi restarts the current track at the same position in the metered quality (#138). */
    val forceInstantTranscoding: Boolean = false,
    val behavior: PlaybackBehavior = PlaybackBehavior(),
    val audioFocus: AudioFocusPreferences = AudioFocusPreferences(),
    val headset: HeadsetPreferences = HeadsetPreferences(),
    val notificationButtons: NotificationButtonsPreferences = NotificationButtonsPreferences(),
    val autoActions: AutoActionsPreferences = AutoActionsPreferences(),
    val transitions: TransitionsPreferences = TransitionsPreferences(),
) {
    /** The streaming quality for a network that is [metered] or not. */
    fun qualityFor(metered: Boolean): StreamingQuality = if (metered) meteredQuality else unmeteredQuality

    companion object {
        /** Band centers in Hz (simple 5-band equalizer, no parametric editor). */
        val EQ_BANDS_HZ = listOf(60, 230, 910, 3_600, 14_000)

        /** The gain range of one band, in dB. */
        val EQ_GAIN_DB = -12f..12f

        val CACHE_SIZES_GB = listOf(1, 2, 4, 8)

        /** The download qualities offered (#144): 128 kbps is too poor to keep. */
        val DOWNLOAD_QUALITIES =
            listOf(
                StreamingQuality.Original,
                StreamingQuality.Kbps320,
                StreamingQuality.Kbps256,
                StreamingQuality.Kbps192,
            )
        val PARALLEL_DOWNLOADS = 1..4
        const val DEFAULT_PARALLEL_DOWNLOADS = 2
        const val DEFAULT_CACHE_GB = 2
    }
}
