package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.AuthenticationResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PublicSystemInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QuickConnectResultDto

/** `null` when a required field is missing: the server is not a Jellyfin server we understand. */
internal fun PublicSystemInfoDto.toDomain(): ServerInfo? {
    if (id.isNullOrEmpty() || ':' in id || version == null) return null
    return ServerInfo(ServerId(id), serverName.orEmpty(), version)
}

internal fun AuthenticationResultDto.toDomain(): AuthSession? {
    val userId = user?.id
    if (accessToken.isNullOrEmpty() || serverId.isNullOrEmpty() || ':' in serverId ||
        userId.isNullOrEmpty()
    ) {
        return null
    }
    return AuthSession(ServerId(serverId), userId, user.name.orEmpty(), accessToken)
}

internal fun QuickConnectResultDto.toDomain(): QuickConnectCode? {
    if (code.isNullOrEmpty() || secret.isNullOrEmpty()) return null
    return QuickConnectCode(code, secret)
}
