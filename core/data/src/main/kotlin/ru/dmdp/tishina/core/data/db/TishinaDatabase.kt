package ru.dmdp.tishina.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity

/**
 * Room database for Tishina.
 *
 * **Why one database, two tables, no DAO split:**
 * Every Phase 3 use-case touches both `measurements` and `samples` together (Save inserts
 * both, Detail reads both, Delete cascades from one into the other). Carving the DAO would
 * push transaction coordination into the repository for zero clarity gain.
 *
 * **CHECK constraints via triggers (see [Companion.LENGTH_GUARD_CALLBACK]):**
 * Room 2.8 does not surface CHECK on the `@Entity` annotation. To still enforce the
 * `title <= 80` / `note <= 200` invariants at the DB layer (defense in depth — the
 * use-case is the primary validator), we install BEFORE-INSERT/UPDATE triggers in the
 * post-create callback that ABORT with a constraint violation.
 *
 * **`exportSchema = true`:** writes `core/data/schemas/.../1.json` so Phase 4 migration
 * tests can diff against it. The schema directory is committed.
 */
@Database(
    entities = [MeasurementEntity::class, SampleEntity::class],
    version = TishinaDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class TishinaDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val DATABASE_NAME = "tishina.db"
        const val SCHEMA_VERSION = 1

        /**
         * Callback that installs CHECK-like triggers right after Room executes its initial
         * `CREATE TABLE` statements. Triggers fire on every insert/update of `measurements`
         * and raise `SQLITE_CONSTRAINT_CHECK` with a descriptive message when the length
         * invariants from the spec (FR-6, NFR-12) would be violated.
         */
        val LENGTH_GUARD_CALLBACK: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                installLengthGuards(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Re-create on open as well — idempotent because of IF NOT EXISTS — so older
                // installs that opened the DB before this callback existed pick up the
                // guards on the next launch.
                installLengthGuards(db)
            }

            private fun installLengthGuards(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS measurements_title_length_insert
                    BEFORE INSERT ON measurements
                    FOR EACH ROW
                    WHEN NEW.title IS NOT NULL AND LENGTH(NEW.title) > 80
                    BEGIN
                        SELECT RAISE(ABORT, 'CHECK constraint failed: title length <= 80');
                    END;
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS measurements_title_length_update
                    BEFORE UPDATE ON measurements
                    FOR EACH ROW
                    WHEN NEW.title IS NOT NULL AND LENGTH(NEW.title) > 80
                    BEGIN
                        SELECT RAISE(ABORT, 'CHECK constraint failed: title length <= 80');
                    END;
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS measurements_note_length_insert
                    BEFORE INSERT ON measurements
                    FOR EACH ROW
                    WHEN NEW.note IS NOT NULL AND LENGTH(NEW.note) > 200
                    BEGIN
                        SELECT RAISE(ABORT, 'CHECK constraint failed: note length <= 200');
                    END;
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS measurements_note_length_update
                    BEFORE UPDATE ON measurements
                    FOR EACH ROW
                    WHEN NEW.note IS NOT NULL AND LENGTH(NEW.note) > 200
                    BEGIN
                        SELECT RAISE(ABORT, 'CHECK constraint failed: note length <= 200');
                    END;
                    """.trimIndent(),
                )
            }
        }
    }
}
