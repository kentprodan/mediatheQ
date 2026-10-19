package io.github.kentprodan.mediatheq.core.testing

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.kentprodan.mediatheq.core.database.MediatheqDatabase

/** A fresh in-memory [MediatheqDatabase] on the bundled SQLite driver: plain JVM, no Robolectric (testing skill). */
fun inMemoryDatabase(): MediatheqDatabase =
    Room
        .inMemoryDatabaseBuilder<MediatheqDatabase>()
        .setDriver(BundledSQLiteDriver())
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
