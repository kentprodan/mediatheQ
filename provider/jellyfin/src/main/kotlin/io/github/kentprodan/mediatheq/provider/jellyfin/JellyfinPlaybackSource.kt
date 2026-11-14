package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.model.StreamChoice
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.SubtitleDelivery
import io.github.kentprodan.mediatheq.core.model.SubtitleMethod
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.CodecProfileDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.DeviceProfileDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.DirectPlayProfileDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.MediaSourceDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackInfoResponseDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PlaybackReportDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.ProfileConditionDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.SubtitleProfileDto
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
        choice: StreamChoice,
    ): MqResult<PlaybackStream> {
        val body =
            PlaybackInfoDto(
                server.userId,
                profile.maxStreamingBitrate ?: UNLIMITED,
                profile.toDto(),
                autoOpenLiveStream = false,
                // Jellyfin positions are 100 ns ticks; zero is left out like every default.
                startTimeTicks =
                    choice.startPosition.inWholeMilliseconds
                        .takeIf { it > 0 }
                        ?.times(TICKS_PER_MS),
                audioStreamIndex = choice.audioStreamIndex,
                subtitleStreamIndex = choice.subtitleStreamIndex,
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
                        url =
                            directUrl(
                                baseUrl,
                                itemId.itemId,
                                sourceId,
                                playSessionId,
                                kind = if (profile.videoCodecs != null) "Videos" else "Audio",
                            ),
                        playMethod = PlayMethod.DirectPlay,
                        mediaSourceId = sourceId,
                        playSessionId = playSessionId,
                        canSeek = true,
                        sizeBytes = source.size,
                        subtitles = source.subtitles(baseUrl),
                        audioStreams = source.audioStreams(),
                        audioStreamIndex = choice.audioStreamIndex,
                        subtitleStreamIndex = choice.subtitleStreamIndex,
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
                        // A remux (the streams copied into another container) is reported as what it is.
                        playMethod =
                            if (profile.videoCodecs != null && source.supportsDirectStream) {
                                PlayMethod.DirectStream
                            } else {
                                PlayMethod.Transcode
                            },
                        mediaSourceId = sourceId,
                        playSessionId = playSessionId,
                        canSeek = segmented,
                        segmented = segmented,
                        subtitles = source.subtitles(baseUrl),
                        audioStreams = source.audioStreams(),
                        audioStreamIndex = choice.audioStreamIndex,
                        subtitleStreamIndex = choice.subtitleStreamIndex,
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
        const val TICKS_PER_MS = 10_000L
    }
}

private fun DeviceProfile.toDto() = videoCodecs?.let(::toVideoDto) ?: toAudioDto()

/** A video profile (ADR 0050): video direct play, HLS fMP4 conversions, and the limits of the phone's decoders. */
private fun DeviceProfile.toVideoDto(videoCodecs: List<String>) =
    DeviceProfileDto(
        name = "mediatheQ",
        maxStreamingBitrate = maxStreamingBitrate ?: 1_000_000_000,
        musicStreamingTranscodingBitrate = transcoding.bitrate,
        directPlayProfiles =
            listOf(
                DirectPlayProfileDto(
                    "Video",
                    directPlayContainers.joinToString(","),
                    directPlayCodecs.joinToString(","),
                    videoCodec = videoCodecs.joinToString(","),
                ),
            ),
        transcodingProfiles =
            listOf(
                TranscodingProfileDto(
                    type = "Video",
                    container = transcoding.container,
                    audioCodec = transcoding.codec,
                    protocol = "hls",
                    context = "Streaming",
                    maxAudioChannels = transcoding.maxChannels.toString(),
                    videoCodec = transcoding.videoCodec,
                    breakOnNonKeyFrames = true,
                    minSegments = 1,
                ),
            ),
        codecProfiles =
            listOf(
                CodecProfileDto(
                    "Video",
                    listOfNotNull(
                        condition("LessThanEqual", "Width", MAX_WIDTH),
                        condition("LessThanEqual", "VideoBitDepth", MAX_BIT_DEPTH),
                        // The ranges the display and the decoders take (#276); the server tone-maps the others.
                        condition("EqualsAny", "VideoRangeType", videoRangeTypes.joinToString("|")),
                        maxHeight?.let { condition("LessThanEqual", "Height", it.toString()) },
                    ),
                ),
                CodecProfileDto("VideoAudio", listOf(condition("LessThanEqual", "AudioChannels", MAX_AUDIO_CHANNELS))),
            ),
        containerProfiles = emptyList(),
        subtitleProfiles = subtitleProfiles.map { SubtitleProfileDto(it.format, it.method.name) },
    )

/**
 * The subtitle streams of a `PlaybackInfo` answer with how each comes (#274), `Drop` included (the player still has
 * them in the file); unknown methods are left out.
 * An external file's URL is made absolute and loses `ApiKey` (R25: the header carries the token, R1).
 */
private fun MediaSourceDto.subtitles(baseUrl: String): List<SubtitleDelivery> =
    mediaStreams.filter { it.type == "Subtitle" }.mapNotNull { stream ->
        val method = SubtitleMethod.entries.firstOrNull { it.name == stream.deliveryMethod } ?: return@mapNotNull null
        SubtitleDelivery(
            index = stream.index ?: return@mapNotNull null,
            method = method,
            url =
                stream.deliveryUrl?.takeIf { method == SubtitleMethod.External }?.let {
                    serverUrl(
                        baseUrl,
                        withoutApiKey(it),
                    )
                },
            codec = stream.codec,
            language = stream.language,
            title = stream.displayTitle ?: stream.title,
            isForced = stream.isForced,
            isDefault = stream.isDefault,
        )
    }

/** The audio streams in the file's order (#274), for the player's selection on a direct play. */
private fun MediaSourceDto.audioStreams(): List<MediaStreamInfo> =
    mediaStreams.filter { it.type == "Audio" }.mapNotNull { stream ->
        MediaStreamInfo(stream.index ?: return@mapNotNull null, StreamType.Audio, stream.codec, stream.language)
    }

private fun condition(
    condition: String,
    property: String,
    value: String,
) = ProfileConditionDto(condition, property, value, isRequired = false)

private const val MAX_WIDTH = "3840"
private const val MAX_BIT_DEPTH = "10"
private const val MAX_AUDIO_CHANNELS = "8"

private fun DeviceProfile.toAudioDto() =
    DeviceProfileDto(
        name = "mediatheQ",
        maxStreamingBitrate = maxStreamingBitrate ?: 1_000_000_000,
        musicStreamingTranscodingBitrate = transcoding.bitrate,
        // An empty container or codec list means "any" to Jellyfin: nothing to play as it is = no profile at all.
        directPlayProfiles =
            if (directPlayContainers.isEmpty() || directPlayCodecs.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    DirectPlayProfileDto(
                        "Audio",
                        directPlayContainers.joinToString(","),
                        directPlayCodecs.joinToString(","),
                    ),
                )
            },
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
        audioStreamIndex = stream.audioStreamIndex,
        subtitleStreamIndex = stream.subtitleStreamIndex,
    )

/** The original file, byte-range seekable; [kind] `Audio` or `Videos`. */
internal fun directUrl(
    baseUrl: String,
    itemId: String,
    mediaSourceId: String,
    playSessionId: String,
    kind: String = "Audio",
): String =
    baseUrl
        .toHttpUrl()
        .newBuilder()
        .addPathSegment(kind)
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
