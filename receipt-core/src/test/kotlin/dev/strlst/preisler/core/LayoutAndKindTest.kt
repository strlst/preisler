package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutAndKindTest {
    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = OcrLine(text, BoundingBox(left, top, right, bottom))

    @Test
    fun labelAndAmountOnTheSameHeightFormOneRow() {
        val rows =
            assembleRows(
                listOf(
                    line("¥1,280", 300, 502, 400, 541),
                    line("小計", 10, 440, 80, 480),
                    line("合計", 10, 500, 80, 540),
                ),
            )
        assertEquals(2, rows.size)
        assertEquals(listOf("合計", "¥1,280"), rows[1].lines.map { it.text })
    }

    @Test
    fun sidewaysPhotoIsRotatedUpright() {
        // Text runs along +y; the receipt's top is at large x.
        val doc =
            OcrDocument(
                lines =
                    listOf(
                        line("お預り", 1698, 1123, 1750, 1265),
                        line("¥500", 1697, 1863, 1743, 1965),
                        line("小計", 2195, 1126, 2247, 1233),
                        line("¥300", 2195, 1864, 2241, 1967),
                    ),
                imageWidth = 4032,
                imageHeight = 3024,
            )
        val rows = assembleRows(uprightLines(doc))
        val texts = rows.map { r -> r.lines.map { it.text } }
        assertEquals(listOf(listOf("小計", "¥300"), listOf("お預り", "¥500")), texts)
    }

    @Test
    fun labelsIgnoreSpacesAndAmounts() {
        val row = Row(listOf(line("合 計", 0, 0, 10, 10), line("¥1,280", 20, 0, 30, 10)))
        assertEquals("合計", labelOf(row))
    }

    @Test
    fun classification() {
        assertEquals(setOf(RowKind.TOTAL), classify("合計"))
        assertEquals(setOf(RowKind.TOTAL), classify("舎計"))
        assertEquals(setOf(RowKind.TENDER), classify("お預り合計"))
        assertEquals(setOf(RowKind.COUNT), classify("合計点数"))
        assertEquals(setOf(RowKind.CHANGE), classify("お"))
        assertEquals(setOf(RowKind.SUBTOTAL, RowKind.TAX), classify("小計(税抜8%)"))
        assertTrue(RowKind.TAX in classify("(内消費税等"))
    }
}
