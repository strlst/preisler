package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {
    private val document =
        OcrDocument(
            lines =
                listOf(
                    OcrLine("合計", BoundingBox(10, 500, 80, 540)),
                    OcrLine("¥1,280", BoundingBox(300, 502, 400, 541)),
                ),
            imageWidth = 480,
            imageHeight = 1200,
        )

    @Test
    fun ocrDocumentRoundTrips() {
        val json = PreislerJson.encodeToString(OcrDocument.serializer(), document)
        assertEquals(document, PreislerJson.decodeFromString(OcrDocument.serializer(), json))
    }

    @Test
    fun receiptResultRoundTripsWithSchemaVersion() {
        val result =
            ReceiptResult(total = 1280, confidence = Confidence.HIGH, taxLines = listOf(TaxLine(10, 1164, 116)))
        val json = PreislerJson.encodeToString(ReceiptResult.serializer(), result)
        assertTrue(json, "\"schemaVersion\": 1" in json)
        assertEquals(result, PreislerJson.decodeFromString(ReceiptResult.serializer(), json))
    }

    @Test
    fun passthroughExtractorKeepsTextAndReportsNoTotal() {
        val result = PassthroughExtractor.extract(document)
        assertNull(result.total)
        assertEquals(Confidence.NONE, result.confidence)
        assertEquals("合計\n¥1,280", result.rawText)
    }
}
