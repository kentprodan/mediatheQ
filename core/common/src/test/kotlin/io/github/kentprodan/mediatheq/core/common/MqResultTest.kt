package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MqResultTest {
    private val success: MqResult<Int> = MqResult.Success(2)
    private val failure: MqResult<Int> = MqResult.Failure(MqError.NotFound)

    @Test
    fun `map transforms a success`() {
        assertThat(success.map { it * 10 }).isEqualTo(MqResult.Success(20))
    }

    @Test
    fun `map keeps a failure unchanged`() {
        assertThat(failure.map { it * 10 }).isEqualTo(failure)
    }

    @Test
    fun `getOrNull returns the value or null`() {
        assertThat(success.getOrNull()).isEqualTo(2)
        assertThat(failure.getOrNull()).isNull()
    }

    @Test
    fun `errorOrNull returns the error or null`() {
        assertThat(failure.errorOrNull()).isEqualTo(MqError.NotFound)
        assertThat(success.errorOrNull()).isNull()
    }
}
