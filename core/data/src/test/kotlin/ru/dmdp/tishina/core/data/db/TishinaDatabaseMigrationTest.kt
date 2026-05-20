package ru.dmdp.tishina.core.data.db

import android.content.Context
import android.os.Build
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration-infra placeholder.
 *
 * Phase 3 leaves the database at v1; no migrations exist yet. The Room 2.8 [androidx.room.testing.MigrationTestHelper]
 * has a Robolectric-incompatible bug (the driver gets a bare file name while Robolectric
 * resolves to an absolute path — `IllegalArgumentException: This driver is configured to
 * open a database named 'X' but 'absolute/path/X' was requested`). The real migration
 * harness will run under instrumentation in Phase Release.
 *
 * What this test still proves:
 * 1. The exported v1 schema JSON exists where Phase 4 will load it from.
 * 2. The database can be opened on a real on-disk file and reopened idempotently — the
 *    same "no migrations registered" path Phase 4 will tighten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(TEST_DB).delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(TEST_DB).delete()
    }

    @Test
    fun `v1 schema export is present at the expected path`() {
        val schema = File("schemas/${TishinaDatabase::class.qualifiedName}/1.json")
        assertTrue(
            "Expected exported schema at $schema. Run :core:data:kspDebugKotlin to regenerate.",
            schema.exists(),
        )
        // Sanity-check: the JSON mentions the v1 version number — if KSP wrote a wrong
        // version we'd notice here.
        assertTrue(schema.readText().contains("\"version\": 1"))
    }

    @Test
    fun `fresh v1 database opens and reopens without migrations`() {
        val db1 = openDb()
        try {
            assertNotNull(db1.measurementDao())
            assertEquals(1, db1.openHelper.readableDatabase.version)
        } finally {
            db1.close()
        }

        // Re-open on the same file: no migrations registered, so a phantom schema bump
        // would explode here. v1 → v1 should be a no-op.
        val db2 = openDb()
        try {
            assertNotNull(db2.measurementDao())
        } finally {
            db2.close()
        }
    }

    private fun openDb(): TishinaDatabase = Room
        .databaseBuilder(context, TishinaDatabase::class.java, TEST_DB)
        .build()

    private companion object {
        const val TEST_DB = "migration-test-tishina.db"
    }
}
