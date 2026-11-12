package io.github.kentprodan.mediatheq.core.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId

/** What Cleanup deletes (#149): [failed] downloads and the download cache keys [orphans] no download owns. */
internal data class CleanupSelection(
    val failed: List<MediaId>,
    val orphans: List<String>,
)

/**
 * Picks from the whole download [index] (request id -> state, every server's) and the download cache's [cacheKeys]:
 * the failed downloads of [active] (all servers' while signed out), and the keys no download of any server owns. Other
 * accounts' downloads are in the same index and cache (ADR 0036), so they are never orphans.
 */
@UnstableApi
internal fun cleanupSelection(
    index: Map<String, Int>,
    cacheKeys: Set<String>,
    active: ServerId?,
): CleanupSelection =
    CleanupSelection(
        failed =
            index
                .filterValues { it == Download.STATE_FAILED }
                .keys
                .mapNotNull { runCatching { MediaId.parse(it) }.getOrNull() }
                .filter { active == null || it.serverId == active },
        orphans = (cacheKeys - index.keys).sorted(),
    )
