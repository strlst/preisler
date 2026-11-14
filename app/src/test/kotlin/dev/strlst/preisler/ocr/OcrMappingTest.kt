package dev.strlst.preisler.ocr

import dev.strlst.preisler.core.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrMappingTest {
    private val box = BoundingBox(1, 2, 3, 4)

    @Test
    fun portraitRotationSwapsDimensions() {
        val doc = buildOcrDocument(emptySequence(), sensorWidth = 4000, sensorHeight = 3000, rotationDegrees = 90)
        assertEquals(3000, doc.imageWidth)
        assertEquals(4000, doc.imageHeight)
    }

    @Test
    fun uprightRotationKeepsDimensions() {
        val doc = buildOcrDocument(emptySequence(), sensorWidth = 4000, sensorHeight = 3000, rotationDegrees = 180)
        assertEquals(4000, doc.imageWidth)
        assertEquals(3000, doc.imageHeight)
    }

    @Test
    fun dropsBlankAndBoxlessLinesAndTrims() {
        val lines =
            sequenceOf(
                RawLine("  合計 ", box),
                RawLine("   ", box),
                RawLine("¥1,280", null),
                RawLine("¥1,280", box),
            )
        val doc = buildOcrDocument(lines, 100, 100, 0)
        assertEquals(listOf("合計", "¥1,280"), doc.lines.map { it.text })
    }
}
