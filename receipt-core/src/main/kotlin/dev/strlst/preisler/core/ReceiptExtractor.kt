package dev.strlst.preisler.core

/** Turns OCR output into a [ReceiptResult]. The real pipeline lands in Goal 4b. */
fun interface ReceiptExtractor {
    fun extract(document: OcrDocument): ReceiptResult
}

/** Placeholder: reports the raw text and no total. */
object PassthroughExtractor : ReceiptExtractor {
    override fun extract(document: OcrDocument): ReceiptResult =
        ReceiptResult(
            total = null,
            confidence = Confidence.NONE,
            rawText = document.lines.joinToString("\n") { it.text },
        )
}
