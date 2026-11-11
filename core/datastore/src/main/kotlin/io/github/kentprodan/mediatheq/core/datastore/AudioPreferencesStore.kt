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
                        unmeteredQuality = prefs[UNMETERED_QUALITY],
                        meteredQuality = prefs[METERED_QUALITY],
                        streamingCacheGb = prefs[STREAMING_CACHE_GB],
                        downloadsUnmeteredOnly = prefs[DOWNLOADS_UNMETERED_ONLY],
                        downloadQuality = prefs[DOWNLOAD_QUALITY],
                        maxParallelDownloads = prefs[MAX_PARALLEL_DOWNLOADS],
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
                                bluetoothLyrics = prefs[PB_BLUETOOTH_LYRICS],
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
                        fadeInMs = prefs[TR_FADE_IN_MS],
                        fadeOutMs = prefs[TR_FADE_OUT_MS],
                        autoActions =
                            StoredAutoActions(
                                volumePause = prefs[AA_VOLUME_PAUSE],
                                headphonePlay = prefs[AA_HEADPHONE_PLAY],
                                bluetoothPlay = prefs[AA_BLUETOOTH_PLAY],
                                pauseOnTaskRemoval = prefs[AA_PAUSE_TASK_REMOVAL],
                                ignoreRemoteStop = prefs[AA_IGNORE_REMOTE_STOP],
                            ),
                    )
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

        /** The download quality (#144), as its name. */
        suspend fun setDownloadQuality(name: String) {
            dataStore.edit { it[DOWNLOAD_QUALITY] = name }
        }

        suspend fun setMaxParallelDownloads(count: Int) {
            dataStore.edit { it[MAX_PARALLEL_DOWNLOADS] = count }
        }

        /** Removes Wi-Fi only downloads, the download quality and the parallel downloads (#144). */
        suspend fun clearDownloads() {
            dataStore.edit {
                it.remove(DOWNLOADS_UNMETERED_ONLY)
                it.remove(DOWNLOAD_QUALITY)
                it.remove(MAX_PARALLEL_DOWNLOADS)
            }
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
                put(PB_BLUETOOTH_LYRICS, behavior.bluetoothLyrics)
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

        /** Automatic actions (#141): every value at once; null removes it (the default). */
        suspend fun setAutoActions(actions: StoredAutoActions) {
            dataStore.edit {
                it.put(AA_VOLUME_PAUSE, actions.volumePause)
                it.put(AA_HEADPHONE_PLAY, actions.headphonePlay)
                it.put(AA_BLUETOOTH_PLAY, actions.bluetoothPlay)
                it.put(AA_PAUSE_TASK_REMOVAL, actions.pauseOnTaskRemoval)
                it.put(AA_IGNORE_REMOTE_STOP, actions.ignoreRemoteStop)
            }
        }

        /** Transitions (#142): both fade lengths at once; null removes one (the default). */
        suspend fun setFades(
            fadeInMs: Int?,
            fadeOutMs: Int?,
        ) {
            dataStore.edit {
                it.put(TR_FADE_IN_MS, fadeInMs)
                it.put(TR_FADE_OUT_MS, fadeOutMs)
            }
        }

        private fun <T> MutablePreferences.put(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) remove(key) else this[key] = value
        }

        private companion object {
            val UNMETERED_QUALITY = stringPreferencesKey("unmetered_quality")
            val METERED_QUALITY = stringPreferencesKey("metered_quality")
            val STREAMING_CACHE_GB = intPreferencesKey("streaming_cache_gb")
            val DOWNLOADS_UNMETERED_ONLY = booleanPreferencesKey("downloads_unmetered_only")
            val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
            val MAX_PARALLEL_DOWNLOADS = intPreferencesKey("max_parallel_downloads")
            val FORCE_INSTANT_TRANSCODING = booleanPreferencesKey("force_instant_transcoding")
            val PB_PRESERVE_PLAY_NEXT = booleanPreferencesKey("pb_preserve_play_next")
            val PB_REWIND_FIRST = booleanPreferencesKey("pb_rewind_first")
            val PB_CIRCULAR = booleanPreferencesKey("pb_circular")
            val PB_KEEP_PAUSED = booleanPreferencesKey("pb_keep_paused")
            val PB_STOP_REPEAT_ONE = booleanPreferencesKey("pb_stop_repeat_one")
            val PB_RETAIN_STATE = booleanPreferencesKey("pb_retain_state")
            val PB_WEIGHTED_SHUFFLE = booleanPreferencesKey("pb_weighted_shuffle")
            val PB_BLUETOOTH_LYRICS = booleanPreferencesKey("pb_bluetooth_lyrics")
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
            val TR_FADE_IN_MS = intPreferencesKey("tr_fade_in_ms")
            val TR_FADE_OUT_MS = intPreferencesKey("tr_fade_out_ms")
            val AA_VOLUME_PAUSE = booleanPreferencesKey("aa_volume_pause")
            val AA_HEADPHONE_PLAY = booleanPreferencesKey("aa_headphone_play")
            val AA_BLUETOOTH_PLAY = booleanPreferencesKey("aa_bluetooth_play")
            val AA_PAUSE_TASK_REMOVAL = booleanPreferencesKey("aa_pause_task_removal")
            val AA_IGNORE_REMOTE_STOP = booleanPreferencesKey("aa_ignore_remote_stop")
        }
    }

/** Audio preferences as stored; any field may be missing or out of date. */
data class StoredAudioPreferences(
    val unmeteredQuality: String? = null,
    val meteredQuality: String? = null,
    val streamingCacheGb: Int? = null,
    val downloadsUnmeteredOnly: Boolean? = null,
    val downloadQuality: String? = null,
    val maxParallelDownloads: Int? = null,
    val forceInstantTranscoding: Boolean? = null,
    val behavior: StoredPlaybackBehavior = StoredPlaybackBehavior(),
    val outside: StoredOutsideControls = StoredOutsideControls(),
    val autoActions: StoredAutoActions = StoredAutoActions(),
    val fadeInMs: Int? = null,
    val fadeOutMs: Int? = null,
)

/** Automatic actions as stored (#141); `null` = not set. */
data class StoredAutoActions(
    val volumePause: Boolean? = null,
    val headphonePlay: Boolean? = null,
    val bluetoothPlay: Boolean? = null,
    val pauseOnTaskRemoval: Boolean? = null,
    val ignoreRemoteStop: Boolean? = null,
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
    val bluetoothLyrics: Boolean? = null,
)
