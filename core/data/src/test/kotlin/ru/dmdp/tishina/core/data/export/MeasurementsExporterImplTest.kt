package ru.dmdp.tishina.core.data.export

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.room.Room
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.data.db.TishinaDatabase
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * End-to-end test for [MeasurementsExporterImpl] running against in-memory Room.
 *
 * Verifies the full export pipeline:
 *  1. Repository observe → CSV serializer → ContentResolver-backed OutputStream.
 *  2. Filter semantics (ALL vs ByIds) actually narrow the row set.
 *  3. Errors from ContentResolver propagate as `Result.failure` without crashing.
 *
 * Robolectric is required because the in-memory ContentResolver lookup happens via
 * `RuntimeEnvironment.getApplication().contentResolver`. The OutputStream the resolver
 * returns is provided by a mock — that way we get full control over the bytes written
 * without touching the device filesystem.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementsExporterImplTest {

    private lateinit var database: TishinaDatabase
    private lateinit var dao: MeasurementDao
    private lateinit var repository: MeasurementRepositoryImpl
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TishinaDatabase::class.java)
            .addCallback(TishinaDatabase.LENGTH_GUARD_CALLBACK)
            .allowMainThreadQueries()
            .build()
        dao = database.measurementDao()
        repository = MeasurementRepositoryImpl(dao, UnconfinedTestDispatcher())
        // Use a real-but-isolated ContentResolver from the Robolectric app; we mock the
        // openOutputStream return value via a stub ContentProvider would be heavier than
        // needed — instead we test the exporter with a mocked resolver in the failure path
        // and with a captured buffer in the success path.
        contentResolver = mockk(relaxed = false)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newMeasurement(
        createdAt: Long = 1_700_000_000_000L,
        title: String? = null,
        note: String? = null,
        avgDb: Float = 40f,
    ): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAt,
        durationMs = 5_000L,
        avgDb = avgDb,
        minDb = avgDb - 5f,
        maxDb = avgDb + 5f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
        samples = listOf(SoundSample(db = avgDb, timestampMs = 0L)),
    )

    private fun exporter(): MeasurementsExporterImpl = MeasurementsExporterImpl(
        dao = dao,
        contentResolver = contentResolver,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun captureBytesFor(targetUri: String): ByteArrayOutputStream {
        val buffer = ByteArrayOutputStream()
        val uri = Uri.parse(targetUri)
        coEvery { contentResolver.openOutputStream(uri) } returns buffer
        return buffer
    }

    @Test
    fun `seed three measurements and export ALL writes header plus three rows`() = runTest {
        repository.save(newMeasurement(createdAt = 1_000L, title = "Office"))
        repository.save(newMeasurement(createdAt = 2_000L, title = "Street"))
        repository.save(newMeasurement(createdAt = 3_000L, title = null))

        val buffer = captureBytesFor("content://export/all.csv")
        val result = exporter().export("content://export/all.csv", ExportFilter.All)

        assertTrue("exporter failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(3, result.getOrNull())

        val text = stripBom(buffer.toByteArray())
        val lines = text.split("\r\n")
        // 1 header + 3 rows + 1 empty trailing string = 5
        assertEquals(5, lines.size)
        assertTrue(lines[0].startsWith("id,"))
        // Descending by createdAt — "Street" (3000) appears before "Office" (1000); the null-titled row is first.
        assertTrue("row1 should have null title and note: ${lines[1]}", lines[1].endsWith(",,"))
        assertTrue("row2: ${lines[2]}", lines[2].contains(",Street,"))
        assertTrue("row3: ${lines[3]}", lines[3].contains(",Office,"))
    }

    @Test
    fun `export ByIds emits only matching rows`() = runTest {
        val id1 = repository.save(newMeasurement(createdAt = 1_000L, title = "A"))
        repository.save(newMeasurement(createdAt = 2_000L, title = "B"))
        val id3 = repository.save(newMeasurement(createdAt = 3_000L, title = "C"))

        val buffer = captureBytesFor("content://export/subset.csv")
        val result = exporter().export(
            "content://export/subset.csv",
            ExportFilter.ByIds(setOf(id1, id3)),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        val text = stripBom(buffer.toByteArray())
        assertTrue("id1 row missing: $text", text.contains(",A,"))
        assertTrue("id3 row missing: $text", text.contains(",C,"))
        assertFalse("id2 row should be filtered out: $text", text.contains(",B,"))
    }

    @Test
    fun `empty repository exports header-only file`() = runTest {
        val buffer = captureBytesFor("content://export/empty.csv")

        val result = exporter().export("content://export/empty.csv", ExportFilter.All)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        val text = stripBom(buffer.toByteArray())
        assertEquals("id,createdAt,durationMs,avgDb,minDb,maxDb,title,note\r\n", text)
    }

    @Test
    fun `ByIds with empty set exports header-only file`() = runTest {
        repository.save(newMeasurement(title = "ignored"))
        val buffer = captureBytesFor("content://export/empty-ids.csv")

        val result = exporter().export("content://export/empty-ids.csv", ExportFilter.ByIds(emptySet()))

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        val text = stripBom(buffer.toByteArray())
        // Only header line + CRLF.
        assertEquals(1, text.count { it == '\n' })
    }

    @Test
    fun `null OutputStream from ContentResolver returns Result failure`() = runTest {
        repository.save(newMeasurement(title = "x"))
        val uri = Uri.parse("content://export/null-stream.csv")
        coEvery { contentResolver.openOutputStream(uri) } returns null

        val result = exporter().export("content://export/null-stream.csv", ExportFilter.All)

        assertFalse(result.isSuccess)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `IOException from ContentResolver returns Result failure not crash`() = runTest {
        repository.save(newMeasurement(title = "x"))
        val uri = Uri.parse("content://export/io-error.csv")
        coEvery { contentResolver.openOutputStream(uri) } throws IOException("disk full")

        val result = exporter().export("content://export/io-error.csv", ExportFilter.All)

        assertFalse(result.isSuccess)
        val cause = result.exceptionOrNull()
        assertTrue("expected IOException, got $cause", cause is IOException)
    }

    @Test
    fun `OutputStream write throws IOException returns Result failure`() = runTest {
        repository.save(newMeasurement(title = "x"))
        val uri = Uri.parse("content://export/write-error.csv")
        val brokenStream = object : OutputStream() {
            override fun write(b: Int): Unit = throw IOException("simulated write failure")
        }
        coEvery { contentResolver.openOutputStream(uri) } returns brokenStream

        val result = exporter().export("content://export/write-error.csv", ExportFilter.All)

        assertFalse(result.isSuccess)
    }

    @Test
    fun `output bytes are valid UTF-8 with Cyrillic title preserved`() = runTest {
        repository.save(newMeasurement(title = "Кухня", note = "соседи"))
        val buffer = captureBytesFor("content://export/cyrillic.csv")

        val result = exporter().export("content://export/cyrillic.csv", ExportFilter.All)

        assertTrue(result.isSuccess)
        val text = stripBom(buffer.toByteArray())
        assertTrue("Cyrillic title missing: $text", text.contains("Кухня"))
        assertTrue("Cyrillic note missing: $text", text.contains("соседи"))
    }

    private fun stripBom(bytes: ByteArray): String {
        val bomLen = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        return String(bytes, bomLen, bytes.size - bomLen, StandardCharsets.UTF_8)
    }
}
