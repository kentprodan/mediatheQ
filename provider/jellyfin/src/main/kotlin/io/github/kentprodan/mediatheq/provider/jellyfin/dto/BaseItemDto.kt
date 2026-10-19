package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fields of a library item we read; which are filled depends on the item type and the `fields` we request. */
@Serializable
internal data class BaseItemDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("AlbumArtists") val albumArtists: List<NameIdPairDto> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<NameIdPairDto> = emptyList(),
    @SerialName("NormalizationGain") val normalizationGain: Double? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    /** Image type -> (tag -> BlurHash). */
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
)
