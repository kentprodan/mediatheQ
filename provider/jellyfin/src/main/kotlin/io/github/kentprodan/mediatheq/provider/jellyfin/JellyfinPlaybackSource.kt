package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.DeviceProfileDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.DirectPlayProfileDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackInfoResponseDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackReportDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.TranscodingProfileDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject

/** [PlaybackSource] for Jellyfin 12: `PlaybackInfo` with our device profile, and the `/Sessions/Playing` reports. */
class JellyfinPlaybackSource internal constructor(
    private val http: JellyfinHttp,
    private val clientInfo: ClientInfo,
) : PlaybackSource {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher), clientInfo)

    override suspend fun stream(
        server: SignedInServer,
        itemId: MediaId,
        profile: DeviceProfile,
    ): MqResult<PlaybackStream> {
        val body =
            PlaybackInfoDto(
                server.userId,
                profile.maxStreamingBitrate ?: UNLIMITED,
                profile.toDto(),
                autoOpenLiveStream = false,
            )
        val response =
            http.post(
                server.connection,
                "Items/${itemId.itemId}/PlaybackInfo",
                PlaybackInfoResponseDto.serializer(),
                http.jsonBody(PlaybackInfoDto.serializer(), body),
            ) { it }
        val info =
            when (response) {
                is MqResult.Failure -> return response
                is MqResult.Success -> response.value
            }
        if (info.errorCode != null) return MqResult.Failure(MqError.Client(0, "playback: ${info.errorCode}"))
        val source = info.mediaSources.firstOrNull()
        val sourceId = source?.id
        val playSessionId = info.playSessionId
        if (source == null || sourceId == null || playSessionId == null) {
            return MqResult.Failure(MqError.Client(0, "playback: no media source"))
        }
        val baseUrl = server.connection.baseUrl
        val transcodingUrl = source.transcodingUrl
        return when {
            source.supportsDirectPlay -> {
                MqResult.Success(
                    PlaybackStream(
                        itemId = itemId,
                        url = directUrl(baseUrl, itemId.itemId, sourceId, playSessionId),
                        playMethod = PlayMethod.DirectPlay,
                        mediaSourceId = sourceId,
                        playSessionId = playSessionId,
                        canSeek = true,
                        sizeBytes = source.size,
                    ),
                )
            }

            transcodingUrl != null -> {
                // HLS playlists list every segment, converted on demand, so they seek; a live progressive transcode
                // answers "Accept-Ranges: none" (R12). The playlists carry no token once ApiKey is stripped (R15).
                val segmented = source.transcodingSubProtocol == "hls"
                MqResult.Success(
                    PlaybackStream(
                        itemId = itemId,
                        url = serverUrl(baseUrl, withoutApiKey(transcodingUrl)),
                        playMethod = PlayMethod.Transcode,
                        mediaSourceId = sourceId,
                        playSessionId = playSessionId,
                        canSeek = segmented,
                        segmented = segmented,
                    ),
                )
            }

            else -> {
                MqResult.Failure(MqError.Client(0, "playback: no compatible stream"))
            }
        }
    }

    override fun streamHeaders(server: SignedInServer): Map<String, String> =
        mapOf("Authorization" to authorizationHeader(clientInfo, server.connection))

    override suspend fun reportStart(
        server: SignedInServer,
        report: PlaybackReport,
    ): MqResult<Unit> = report(server, "Sessions/Playing", report.toDto())

    override suspend fun reportProgress(
        server: SignedInServer,
        report: PlaybackReport,
    ): MqResult<Unit> = report(server, "Sessions/Playing/Progress", report.toDto())

    override suspend fun reportStopped(
        server: SignedInServer,
        report: PlaybackReport,
        failed: Boolean,
    ): MqResult<Unit> =
        report(
            server,
            "Sessions/Playing/Stopped",
            report.toDto().copy(isPaused = null, playMethod = null, canSeek = null, failed = failed),
        )

    private suspend fun report(
        server: SignedInServer,
        path: String,
        body: PlaybackReportDto,
    ): MqResult<Unit> = http.post(server.connection, path, http.jsonBody(PlaybackReportDto.serializer(), body))

    private companion object {
        /** "No cap" for Jellyfin, which treats a missing value as its own (lower) default. */
        const val UNLIMITED = 1_000_000_000
    }
}

private fun DeviceProfile.toDto() =
    DeviceProfileDto(
        name = "mediatheQ",
        maxStreamingBitrate = maxStreamingBitrate ?: 1_000_000_000,
        musicStreamingTranscodingBitrate = transcoding.bitrate,
        directPlayProfiles =
            listOf(
                DirectPlayProfileDto(
                    "Audio",
                    directPlayContainers.joinToString(","),
                    directPlayCodecs.joinToString(","),
                ),
            ),
        transcodingProfiles =
            listOf(
                TranscodingProfileDto(
                    type = "Audio",
                    container = transcoding.container,
                    audioCodec = transcoding.codec,
                    protocol = if (transcoding.segmented) "hls" else "http",
                    context = "Streaming",
                    maxAudioChannels = transcoding.maxChannels.toString(),
                ),
            ),
        codecProfiles = emptyList(),
        containerProfiles = emptyList(),
        subtitleProfiles = emptyList(),
    )

private fun PlaybackReport.toDto() =
    PlaybackReportDto(
        itemId = stream.itemId.itemId,
        mediaSourceId = stream.mediaSourceId,
        playSessionId = stream.playSessionId,
        // Jellyfin positions are 100 ns ticks (jellyfin skill, section 5).
        positionTicks = position.inWholeMilliseconds * 10_000,
        isPaused = isPaused,
        playMethod = stream.playMethod.name,
        canSeek = stream.canSeek,
    )

/** The original file, byte-range seekable. */
internal fun directUrl(
    baseUrl: String,
    itemId: String,
    mediaSourceId: String,
    playSessionId: String,
): String =
    baseUrl
        .toHttpUrl()
        .newBuilder()
        .addPathSegment("Audio")
        .addPathSegment(itemId)
        .addPathSegment("stream")
        .addQueryParameter("static", "true")
        .addQueryParameter("mediaSourceId", mediaSourceId)
        .addQueryParameter("playSessionId", playSessionId)
        .build()
        .toString()

/**
 * Jellyfin's transcoding URL carries the token as `ApiKey`; we never put the token in a URL (R1, it would end up in
 * caches and logs) and send the header instead.
 */
internal fun withoutApiKey(pathAndQuery: String): String {
    val query = pathAndQuery.substringAfter('?', "")
    if (query.isEmpty()) return pathAndQuery
    val kept = query.split('&').filterNot { it.substringBefore('=').lowercase() in TOKEN_PARAMETERS }
    val path = pathAndQuery.substringBefore('?')
    return if (kept.isEmpty()) path else path + "?" + kept.joinToString("&")
}

private val TOKEN_PARAMETERS = setOf("apikey", "api_key")

/** Joins a server-relative path to [baseUrl] without doubling a base path such as `/jellyfin`. */
internal fun serverUrl(
    baseUrl: String,
    pathAndQuery: String,
): String {
    val base = baseUrl.trimEnd('/')
    val basePath = base.toHttpUrl().encodedPath.trimEnd('/')
    val relative =
        if (basePath.isNotEmpty() &&
            pathAndQuery.startsWith("$basePath/")
        ) {
            pathAndQuery.removePrefix(basePath)
        } else {
            pathAndQuery
        }
    val origin = base.removeSuffix(basePath)
    return origin + basePath + (if (relative.startsWith("/")) relative else "/$relative")
}
