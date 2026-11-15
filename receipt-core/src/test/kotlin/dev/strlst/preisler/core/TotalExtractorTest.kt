package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TotalExtractorTest {
    private fun row(
        y: Int,
        vararg texts: String,
    ) = texts.mapIndexed { i, t -> OcrLine(t, BoundingBox(i * 500, y, i * 500 + 200, y + 40)) }

    private fun extract(vararg rows: List<OcrLine>) = TotalExtractor.extract(OcrDocument(rows.flatMap { it }))

    @Test
    fun totalConfirmedByChangeIsHigh() {
        val result =
            extract(
                row(100, "小計", "¥1,185"),
                row(200, "合計", "¥1,280"),
                row(300, "お預り", "¥2,000"),
                row(400, "お釣り", "¥720"),
            )
        assertEquals(1280L, result.total)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun crossChecksOutvoteAMisreadTotal() {
        val result =
            extract(
                row(100, "小計(税抜8%)", "¥167"),
                row(200, "消費税等(8%)", "¥13"),
                row(300, "合計", "130"),
                row(400, "お預り", "180"),
                row(500, "お釣", "0"),
            )
        assertEquals(180L, result.total)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun withoutTotalOrChecksTheLargestYenAmountWins() {
        // Meal ticket: no 合計, no change. ¥150 appears twice, so plain scoring alone would pick it.
        val result =
            extract(
                row(100, "ごはん(並)", "¥150"),
                row(200, "和風ハンバーグ定食", "¥1,090"),
                row(300, "大盛", "¥150"),
            )
        assertEquals(1090L, result.total)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun noAmountsMeansNoTotal() {
        val result = extract(row(100, "ありがとうございました"))
        assertEquals(null, result.total)
        assertEquals(Confidence.NONE, result.confidence)
    }
}
