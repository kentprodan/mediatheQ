package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.AuthenticationResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PublicSystemInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QuickConnectResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UserDto
import org.junit.Test

class AuthMappersTest {
    private val auth = AuthenticationResultDto(UserDto("user-1", "Ana"), "token", "server-1")

    @Test
    fun `public info without id or version is not a server we understand`() {
        assertThat(PublicSystemInfoDto(id = null, version = "12.2.0").toDomain()).isNull()
        assertThat(PublicSystemInfoDto(id = "a:b", version = "12.2.0").toDomain()).isNull()
        assertThat(PublicSystemInfoDto(id = "server-1", version = null).toDomain()).isNull()
    }

    @Test
    fun `public info without a name maps to an empty name`() {
        assertThat(PublicSystemInfoDto(id = "server-1", version = "12.2.0").toDomain()?.name).isEmpty()
    }

    @Test
    fun `a login result needs a token, a server id and a user id`() {
        assertThat(auth.toDomain()?.userName).isEqualTo("Ana")
        assertThat(auth.copy(accessToken = "").toDomain()).isNull()
        assertThat(auth.copy(serverId = null).toDomain()).isNull()
        assertThat(auth.copy(user = UserDto(id = null)).toDomain()).isNull()
    }

    @Test
    fun `a Quick Connect result needs a code and a secret`() {
        assertThat(QuickConnectResultDto(code = "123456", secret = "s").toDomain()?.code).isEqualTo("123456")
        assertThat(QuickConnectResultDto(code = null, secret = "s").toDomain()).isNull()
        assertThat(QuickConnectResultDto(code = "123456", secret = "").toDomain()).isNull()
    }
}
