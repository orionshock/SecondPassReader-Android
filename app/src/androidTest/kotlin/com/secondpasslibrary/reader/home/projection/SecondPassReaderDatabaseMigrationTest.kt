package com.secondpasslibrary.reader.home.projection

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SecondPassReaderDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("reader-migration"),
        driver = AndroidSQLiteDriver(),
        databaseClass = SecondPassReaderDatabase::class
    )

    @Test
    fun everyShippedSchemaMigratesToCurrent() = runBlocking {
        listOf(1, 2, 3, 4).forEach { version ->
            instrumentation.targetContext.deleteDatabase("reader-migration")
            helper.createDatabase(version).close()
            helper.runMigrationsAndValidate(5).close()
        }
    }

    @Test
    fun version4PreservesAuthoredReaderDataAndRenamesServerCount() = runBlocking {
        instrumentation.targetContext.deleteDatabase("reader-migration")
        helper.createDatabase(4).use { connection ->
            connection.execSQL(
                "INSERT INTO reader_sessions VALUES " +
                    "('account','local','book','server','SERVER_CONFIRMED','ACTIVE',NULL," +
                    "NULL,'',NULL,NULL,NULL,7,1,2)"
            )
            connection.execSQL(
                "INSERT INTO reader_progress VALUES " +
                    "('account','local','epubcfi(/6/2!/4/2:3)',3,'LOCAL_PENDING')"
            )
            connection.execSQL(
                "INSERT INTO reader_annotations VALUES " +
                    "('account','local',NULL,'client','HIGHLIGHT','epubcfi(/6/2!/4/2:3)'," +
                    "'Chapter 1','quote',' pre\t',' suffix',' note\n','YELLOW','time','LOCAL_PENDING')"
            )
        }

        helper.runMigrationsAndValidate(5).use { connection ->
            connection.prepare(
                "SELECT serverAnnotationCount FROM reader_sessions WHERE localSessionId='local'"
            ).use { statement ->
                check(statement.step())
                assertEquals(7, statement.getLong(0))
            }
            connection.prepare(
                "SELECT cfi, prefix, note FROM reader_annotations WHERE clientId='client'"
            ).use { statement ->
                check(statement.step())
                assertEquals("epubcfi(/6/2!/4/2:3)", statement.getText(0))
                assertEquals(" pre\t", statement.getText(1))
                assertEquals(" note\n", statement.getText(2))
            }
        }
    }
}
