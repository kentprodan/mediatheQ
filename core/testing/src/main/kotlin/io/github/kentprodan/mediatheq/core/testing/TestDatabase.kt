package io.github.kentprodan.mediatheq.core.testing

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.kentprodan.mediatheq.core.database.MediatheqDatabase
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * A fresh in-memory [MediatheqDatabase] on the bundled SQLite driver: plain JVM, no Robolectric (testing skill). Pass
 * the test dispatcher as [queryContext] so `advanceUntilIdle()` also waits for database work.
 */
fun inMemoryDatabase(queryContext: CoroutineContext = Dispatchers.IO): MediatheqDatabase =
    Room
        .inMemoryDatabaseBuilder<MediatheqDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        .build()

/** Runs a raw query and returns every row as text, for asserting on tables that have no DAO read. */
suspend fun MediatheqDatabase.rows(sql: String): List<List<String?>> =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList {
                while (statement.step()) add(List(statement.getColumnCount()) { statement.getText(it) })
            }
        }
    }
