package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Raw audio preferences, exactly as stored; `:core:data` validates them. They survive sign-out. */
@Singleton
class AudioPreferencesStore
    @Inject
    constructor(
        @param:AudioPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** Stored values; empty preferences when the file cannot be read. */
        val values: Flow<StoredAudioPreferences> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs ->
                    StoredAudioPreferences(
                        bitPerfectUsb = prefs[BIT_PERFECT_USB],
                        equalizerOn = prefs[EQUALIZER_ON],
                        eqPreset = prefs[EQ_PRESET],
                        eqBands = prefs[EQ_BANDS],
                        normalization = prefs[NORMALIZATION],
                        unmeteredQuality = prefs[UNMETERED_QUALITY],
                        meteredQuality = prefs[METERED_QUALITY],
                        streamingCacheGb = prefs[STREAMING_CACHE_GB],
                        downloadsUnmeteredOnly = prefs[DOWNLOADS_UNMETERED_ONLY],
                        forceInstantTranscoding = prefs[FORCE_INSTANT_TRANSCODING],
                        behavior =
                            StoredPlaybackBehavior(
                                preservePlayNextOrder = prefs[PB_PRESERVE_PLAY_NEXT],
                                rewindFirst = prefs[PB_REWIND_FIRST],
                                circular = prefs[PB_CIRCULAR],
                                keepPausedOnSkip = prefs[PB_KEEP_PAUSED],
                                stopRepeatOneOnSkip = prefs[PB_STOP_REPEAT_ONE],
                                retainPlayerState = prefs[PB_RETAIN_STATE],
                                weightedShuffle = prefs[PB_WEIGHTED_SHUFFLE],
                            ),
                        outside =
                            StoredOutsideControls(
                                focusShortAction = prefs[AF_SHORT_ACTION],
                                focusNoResume = prefs[AF_NO_RESUME],
                                headsetSingle = prefs[HB_SINGLE],
                                headsetDouble = prefs[HB_DOUBLE],
                                headsetTriple = prefs[HB_TRIPLE],
                                headsetNext = prefs[HB_NEXT],
                                headsetPrevious = prefs[HB_PREVIOUS],
                                headsetSlow = prefs[HB_SLOW],
                                notificationPrevious = prefs[NB_PREVIOUS],
                                notificationNext = prefs[NB_NEXT],
                                notificationAction1 = prefs[NB_ACTION_1],
                                notificationAction2 = prefs[NB_ACTION_2],
                                notificationAction3 = prefs[NB_ACTION_3],
                            ),
                    )
                }

        suspend fun setBitPerfectUsb(on: Boolean) {
            dataStore.edit { it[BIT_PERFECT_USB] = on }
        }

        suspend fun setEqualizerOn(on: Boolean) {
            dataStore.edit { it[EQUALIZER_ON] = on }
        }

        suspend fun setNormalization(name: String) {
            dataStore.edit { it[NORMALIZATION] = name }
        }

        /** Stores the streaming quality [name] for [metered] networks, or for unmetered ones. */
        suspend fun setStreamingQuality(
            metered: Boolean,
            name: String,
        ) {
            dataStore.edit { it[if (metered) METERED_QUALITY else UNMETERED_QUALITY] = name }
        }

        suspend fun setStreamingCacheGb(gb: Int) {
            dataStore.edit { it[STREAMING_CACHE_GB] = gb }
        }

        suspend fun setDownloadsUnmeteredOnly(on: Boolean) {
            dataStore.edit { it[DOWNLOADS_UNMETERED_ONLY] = on }
        }

        suspend fun setForceInstantTranscoding(on: Boolean) {
            dataStore.edit { it[FORCE_INSTANT_TRANSCODING] = on }
        }

        /** Removes both streaming qualities and instant transcoding (#138): the defaults apply again. */
        suspend fun clearDecoding() {
            dataStore.edit {
                it.remove(UNMETERED_QUALITY)
                it.remove(METERED_QUALITY)
                it.remove(FORCE_INSTANT_TRANSCODING)
            }
        }

        /** Stores every Playback behavior value at once (#138); null removes it. */
        suspend fun setBehavior(behavior: StoredPlaybackBehavior) {
            dataStore.edit { prefs ->
                fun put(
                    key: Preferences.Key<Boolean>,
                    value: Boolean?,
                ) {
                    if (value == null) prefs.remove(key) else prefs[key] = value
                }
                put(PB_PRESERVE_PLAY_NEXT, behavior.preservePlayNextOrder)
                put(PB_REWIND_FIRST, behavior.rewindFirst)
                put(PB_CIRCULAR, behavior.circular)
                put(PB_KEEP_PAUSED, behavior.keepPausedOnSkip)
                put(PB_STOP_REPEAT_ONE, behavior.stopRepeatOneOnSkip)
                put(PB_RETAIN_STATE, behavior.retainPlayerState)
                put(PB_WEIGHTED_SHUFFLE, behavior.weightedShuffle)
            }
        }

        /** Audio focus (#139): every value at once; null removes it (the default). */
        suspend fun setAudioFocus(
            shortAction: String?,
            noResume: Boolean?,
        ) {
            dataStore.edit {
                it.put(AF_SHORT_ACTION, shortAction)
                it.put(AF_NO_RESUME, noResume)
            }
        }

        /** Headset buttons (#139): single / double / triple click, next / previous button, slow clicks. */
        suspend fun setHeadset(
            actions: Pair<List<String>, Pair<String, String>>?,
            slow: Boolean?,
        ) {
            dataStore.edit {
                it.put(HB_SINGLE, actions?.first?.get(0))
                it.put(HB_DOUBLE, actions?.first?.get(1))
                it.put(HB_TRIPLE, actions?.first?.get(2))
                it.put(HB_NEXT, actions?.second?.first)
                it.put(HB_PREVIOUS, actions?.second?.second)
                it.put(HB_SLOW, slow)
            }
        }

        /** Notification buttons (#139): the three actions, previous and next shown. */
        suspend fun setNotificationButtons(
            actions: List<String>?,
            showPrevious: Boolean?,
            showNext: Boolean?,
        ) {
            dataStore.edit {
                it.put(NB_ACTION_1, actions?.get(0))
                it.put(NB_ACTION_2, actions?.get(1))
                it.put(NB_ACTION_3, actions?.get(2))
                it.put(NB_PREVIOUS, showPrevious)
                it.put(NB_NEXT, showNext)
            }
        }

        private fun <T> MutablePreferences.put(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) remove(key) else this[key] = value
        }

        /** Stores the preset [name], and the custom band gains when [bands] is not `null`, in one write. */
        suspend fun setEq(
            name: String,
            bands: String?,
        ) {
            dataStore.edit { prefs ->
                prefs[EQ_PRESET] = name
                bands?.let { prefs[EQ_BANDS] = it }
            }
        }

        private companion object {
            val BIT_PERFECT_USB = booleanPreferencesKey("bit_perfect_usb")
            val EQUALIZER_ON = booleanPreferencesKey("equalizer_on")
            val EQ_PRESET = stringPreferencesKey("eq_preset")
            val EQ_BANDS = stringPreferencesKey("eq_bands")
            val NORMALIZATION = stringPreferencesKey("normalization")
            val UNMETERED_QUALITY = stringPreferencesKey("unmetered_quality")
            val METERED_QUALITY = stringPreferencesKey("metered_quality")
            val STREAMING_CACHE_GB = intPreferencesKey("streaming_cache_gb")
            val DOWNLOADS_UNMETERED_ONLY = booleanPreferencesKey("downloads_unmetered_only")
            val FORCE_INSTANT_TRANSCODING = booleanPreferencesKey("force_instant_transcoding")
            val PB_PRESERVE_PLAY_NEXT = booleanPreferencesKey("pb_preserve_play_next")
            val PB_REWIND_FIRST = booleanPreferencesKey("pb_rewind_first")
            val PB_CIRCULAR = booleanPreferencesKey("pb_circular")
            val PB_KEEP_PAUSED = booleanPreferencesKey("pb_keep_paused")
            val PB_STOP_REPEAT_ONE = booleanPreferencesKey("pb_stop_repeat_one")
            val PB_RETAIN_STATE = booleanPreferencesKey("pb_retain_state")
            val PB_WEIGHTED_SHUFFLE = booleanPreferencesKey("pb_weighted_shuffle")
            val AF_SHORT_ACTION = stringPreferencesKey("af_short_action")
            val AF_NO_RESUME = booleanPreferencesKey("af_no_resume")
            val HB_SINGLE = stringPreferencesKey("hb_single")
            val HB_DOUBLE = stringPreferencesKey("hb_double")
            val HB_TRIPLE = stringPreferencesKey("hb_triple")
            val HB_NEXT = stringPreferencesKey("hb_next")
            val HB_PREVIOUS = stringPreferencesKey("hb_previous")
            val HB_SLOW = booleanPreferencesKey("hb_slow")
            val NB_PREVIOUS = booleanPreferencesKey("nb_previous")
            val NB_NEXT = booleanPreferencesKey("nb_next")
            val NB_ACTION_1 = stringPreferencesKey("nb_action_1")
            val NB_ACTION_2 = stringPreferencesKey("nb_action_2")
            val NB_ACTION_3 = stringPreferencesKey("nb_action_3")
        }
    }

/** Audio preferences as stored; any field may be missing or out of date. */
data class StoredAudioPreferences(
    val bitPerfectUsb: Boolean? = null,
    val equalizerOn: Boolean? = null,
    val eqPreset: String? = null,
    /** Custom band gains in dB, comma separated. */
    val eqBands: String? = null,
    val normalization: String? = null,
    val unmeteredQuality: String? = null,
    val meteredQuality: String? = null,
    val streamingCacheGb: Int? = null,
    val downloadsUnmeteredOnly: Boolean? = null,
    val forceInstantTranscoding: Boolean? = null,
    val behavior: StoredPlaybackBehavior = StoredPlaybackBehavior(),
    val outside: StoredOutsideControls = StoredOutsideControls(),
)

/** Audio focus, headset and notification buttons as stored (#139); `null` = not set. */
data class StoredOutsideControls(
    val focusShortAction: String? = null,
    val focusNoResume: Boolean? = null,
    val headsetSingle: String? = null,
    val headsetDouble: String? = null,
    val headsetTriple: String? = null,
    val headsetNext: String? = null,
    val headsetPrevious: String? = null,
    val headsetSlow: Boolean? = null,
    val notificationPrevious: Boolean? = null,
    val notificationNext: Boolean? = null,
    val notificationAction1: String? = null,
    val notificationAction2: String? = null,
    val notificationAction3: String? = null,
)

/** Playback behavior as stored (#138); `null` = not set. */
data class StoredPlaybackBehavior(
    val preservePlayNextOrder: Boolean? = null,
    val rewindFirst: Boolean? = null,
    val circular: Boolean? = null,
    val keepPausedOnSkip: Boolean? = null,
    val stopRepeatOneOnSkip: Boolean? = null,
    val retainPlayerState: Boolean? = null,
    val weightedShuffle: Boolean? = null,
)
