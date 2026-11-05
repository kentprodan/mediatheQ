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
    /** With `fields=Genres`: the item's genres (their ids are not the ones `/Genres` lists, R17). */
    @SerialName("GenreItems") val genreItems: List<NameIdPairDto> = emptyList(),
    /** With `fields=Studios`: an album's record labels (R18). */
    @SerialName("Studios") val studios: List<NameIdPairDto> = emptyList(),
    @SerialName("NormalizationGain") val normalizationGain: Double? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    /** Only with `enableImageTypes` naming Backdrop (R21). */
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    /** Image type -> (tag -> BlurHash). */
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
    /** With `fields=MediaSources`: the file (size) and its streams; `MediaStreams` then is not sent on the item (R19). */
    @SerialName("MediaSources") val mediaSources: List<MediaSourceDto> = emptyList(),
    /** With `fields=Overview`: the album's description or the artist's biography. */
    @SerialName("Overview") val overview: String? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
)
