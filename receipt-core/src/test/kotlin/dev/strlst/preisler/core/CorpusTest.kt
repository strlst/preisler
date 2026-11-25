package dev.strlst.preisler.core

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * One test per real receipt in `src/test/resources/receipts/` (AGENTS.md §5).
 * The merge gate is 100%: every fixture must pass.
 */
@RunWith(Parameterized::class)
class CorpusTest(
    private val name: String,
    private val dir: File,
) {
    /** Reference values read from the paper receipt by the user; `date` is null when not legible. */
    @Serializable
    data class Expected(
        val total: Long,
        val date: String?,
        val category: Category,
    )

    @Test
    fun extractsTotalDateAndCategory() {
        val document = PreislerJson.decodeFromString(OcrDocument.serializer(), File(dir, "ocr.json").readText())
        val expected = PreislerJson.decodeFromString(Expected.serializer(), File(dir, "expected.json").readText())

        val result = ReceiptReader.extract(document)

        assertEquals("total for $name", expected.total, result.total)
        assertNotEquals("confidence for $name", Confidence.NONE, result.confidence)
        assertEquals("date for $name", expected.date, result.date)
        assertEquals("category for $name", expected.category, result.category)
    }

    companion object {
        /** Guards against the corpus silently going missing from the classpath. */
        private const val MIN_CORPUS_SIZE = 23

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<Any>> {
            val root = File(checkNotNull(CorpusTest::class.java.getResource("/receipts")).toURI())
            val dirs = root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
            assertTrue("corpus has ${dirs.size} receipts, expected ≥ $MIN_CORPUS_SIZE", dirs.size >= MIN_CORPUS_SIZE)
            return dirs.map { arrayOf(it.name, it) }
        }
    }
}
