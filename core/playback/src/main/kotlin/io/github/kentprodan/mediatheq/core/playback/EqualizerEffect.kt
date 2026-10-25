package io.github.kentprodan.mediatheq.core.playback

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.sqrt

/** One DynamicsProcessing EQ band: it covers frequencies up to [cutoffHz] (bands are set by upper edge, not center). */
internal data class EqBandSetting(
    val cutoffHz: Float,
    val gainDb: Float,
)

/** The equalizer curve for a list of band centers and gains (media3 skill, section 7). Pure. */
internal object EqualizerCurve {
    /** The highest band reaches up to the top of hearing. */
    private const val TOP_HZ = 20_000f

    /** Bands with upper edges halfway (geometrically) between neighboring centers. */
    fun bands(
        centersHz: List<Int>,
        gainsDb: List<Float>,
    ): List<EqBandSetting> =
        centersHz.indices.map { i ->
            val cutoff = centersHz.getOrNull(i + 1)?.let { sqrt(centersHz[i].toFloat() * it) } ?: TOP_HZ
            EqBandSetting(cutoff, gainsDb[i])
        }

    /** Input gain that keeps the loudest boost from clipping: minus the largest positive gain. */
    fun preGainDb(gainsDb: List<Float>): Float = 0f - (gainsDb.maxOrNull() ?: 0f).coerceAtLeast(0f)
}

/**
 * The equalizer on the player's audio session, through [DynamicsProcessing]. Enabled only in
 * [AudioMode.Processed]; offload is off then, so the effect really gets the samples. Main thread only.
 */
internal class EqualizerEffect(
    private val centersHz: List<Int>,
) {
    private var effect: DynamicsProcessing? = null
    private var sessionId: Int? = null

    /** Applies [gainsDb] to [audioSessionId] and turns the effect on or off; re-creates it when the session changed. */
    fun apply(
        audioSessionId: Int,
        enabled: Boolean,
        gainsDb: List<Float>,
    ) {
        if (!enabled && effect == null) return
        val dp = effectFor(audioSessionId) ?: return
        EqualizerCurve.bands(centersHz, gainsDb).forEachIndexed { i, band ->
            dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, band.cutoffHz, band.gainDb))
        }
        dp.setInputGainAllChannelsTo(EqualizerCurve.preGainDb(gainsDb))
        dp.enabled = enabled
    }

    fun release() {
        effect?.release()
        effect = null
        sessionId = null
    }

    private fun effectFor(audioSessionId: Int): DynamicsProcessing? {
        if (sessionId == audioSessionId) return effect
        release()
        val config =
            DynamicsProcessing.Config
                .Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    // channelCount =
                    2,
                    // preEqInUse =
                    true,
                    centersHz.size,
                    // mbcInUse =
                    false,
                    0,
                    // postEqInUse =
                    false,
                    0,
                    // limiterInUse =
                    false,
                ).build()
        effect =
            try {
                DynamicsProcessing(0, audioSessionId, config)
            } catch (e: RuntimeException) {
                // Some devices have no DynamicsProcessing; music plays on without the EQ.
                Log.w(TAG, "Equalizer unavailable", e)
                null
            }
        sessionId = audioSessionId
        return effect
    }

    private companion object {
        const val TAG = "EqualizerEffect"
    }
}
