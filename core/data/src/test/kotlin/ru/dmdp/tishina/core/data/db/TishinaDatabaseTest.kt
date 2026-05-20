package ru.dmdp.tishina.core.data.db

import android.os.Build
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Smoke tests for [TishinaDatabase] construction. Lets us catch DAO/entity wiring mistakes
 * (missing `@Database(entities = [...])`, wrong abstract method signature) without paying
 * for a full DAO test run.
 *
 * Also asserts that the schema export pipeline is wired up — Phase 4 migrations rely on
 * the v1 JSON living under `core/data/schemas/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaDatabaseTest {

    @Test
    fun `database opens and exposes MeasurementDao`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database = Room
            .inMemoryDatabaseBuilder(context, TishinaDatabase::class.java)
            .build()

        try {
            assertNotNull(database.measurementDao())
            assertEquals(
                "expected schema version 1 for Phase 3",
                1,
                database.openHelper.readableDatabase.version,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `room schema for version 1 is exported and committed to the repository`() {
        // KSP writes the schema to `core/data/schemas/<qualified-db-name>/<version>.json`
        // whenever the `room.schemaLocation` argument is honored. If this file is missing,
        // either the KSP arg is mis-spelled or someone committed without running the build.
        val schemaFile = File("schemas/ru.dmdp.tishina.core.data.db.TishinaDatabase/1.json")
        assertEquals(
            "Expected exported Room schema at $schemaFile (run :core:data:kspDebugKotlin to regenerate)",
            true,
            schemaFile.exists(),
        )
    }
}
