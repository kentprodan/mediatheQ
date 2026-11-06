package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where listening to each album stopped (#132), kept on the device (the server keeps no music positions, R20). */
interface ResumeRepository {
    /** Where [albumId] was left, as recorded; [resumeTarget] turns it into what Resume plays. */
    fun resume(albumId: MediaId): Flow<ResumePoint?>

    /** [trackId] played up to [position]; remembered for its album (a track without one is ignored). */
    suspend fun record(
        trackId: MediaId,
        position: Duration,
    )
}

/**
 * What Resume plays from [resume] on an album of [tracks] in play order: the same track and position, or, when the
 * track was played to (within [ENDED] of) its end, the next track from the start. `null` (no Resume) when nothing was
 * recorded, the track left the album, or the last track was finished.
 */
fun resumeTarget(
    resume: ResumePoint?,
    tracks: List<Track>,
): ResumePoint? {
    resume ?: return null
    val index = tracks.indexOfFirst { it.id == resume.trackId }
    if (index < 0) return null
    val track = tracks[index]
    // A short interlude counts as finished only in its last half, not from its first second.
    val ended = track.duration > Duration.ZERO && resume.position >= track.duration - minOf(ENDED, track.duration / 2)
    if (!ended) return resume
    return tracks.getOrNull(index + 1)?.let { ResumePoint(it.id, Duration.ZERO) }
}

private val ENDED = 10.seconds
