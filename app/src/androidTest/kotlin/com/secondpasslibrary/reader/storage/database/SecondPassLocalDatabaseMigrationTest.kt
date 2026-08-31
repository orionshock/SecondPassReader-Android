package com.secondpasslibrary.reader.storage.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecondPassLocalDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("reader-migration"),
        driver = AndroidSQLiteDriver(),
        databaseClass = SecondPassLocalDatabase::class
    )

    @Test
    fun version6IsTheMigrationBaseline() = runBlocking {
        instrumentation.targetContext.deleteDatabase("reader-migration")
        helper.createDatabase(6).use { connection ->
            connection.prepare(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='reader_sessions'"
            ).use { statement -> assertTrue(statement.step()) }
            connection.prepare(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name='reader_continuation_outcomes'"
            ).use { statement -> assertTrue(statement.step()) }
        }
    }
}
