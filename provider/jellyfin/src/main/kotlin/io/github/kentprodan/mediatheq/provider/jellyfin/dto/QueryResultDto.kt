package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A page of `BaseItemDto`s (`/Items`, `/Artists/AlbumArtists`, `/Playlists/{id}/Items`). */
@Serializable
internal data class QueryResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)
