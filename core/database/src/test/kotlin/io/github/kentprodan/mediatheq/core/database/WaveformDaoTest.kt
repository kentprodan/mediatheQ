package io.github.kentprodan.mediatheq.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.WaveformEntity
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class WaveformDaoTest {
    private val database = inMemoryDatabase()
    private val dao = database.waveformDao()

    @After
    fun close() = database.close()

    @Test
    fun `a saved waveform reads back with its size, and nothing is stored for other tracks`() =
        runTest {
            val saved = WaveformEntity("s", "t1", 1234, byteArrayOf(0, 127, -1))

            dao.save(saved)

            assertThat(dao.sizeOf("s", "t1")).isEqualTo(1234)
            assertThat(dao.sizeOf("s", "t2")).isNull()
            assertThat(dao.sizeOf("other", "t1")).isNull()
            dao.waveform("s", "t1").test { assertThat(awaitItem()).isEqualTo(saved) }
        }

    @Test
    fun `a new analysis replaces the old one and the flow follows`() =
        runTest {
            dao.waveform("s", "t1").test {
                assertThat(awaitItem()).isNull()

                dao.save(WaveformEntity("s", "t1", 10, byteArrayOf(1)))
                assertThat(awaitItem()?.sizeBytes).isEqualTo(10)

                // No decoder for this file: stored without levels, so it is not tried again.
                dao.save(WaveformEntity("s", "t1", 20, null))
                val replaced = awaitItem()
                assertThat(replaced?.sizeBytes).isEqualTo(20)
                assertThat(replaced?.levels).isNull()
            }
        }
}
