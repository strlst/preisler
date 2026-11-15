package dev.strlst.preisler.core

/** Turns OCR output into a [ReceiptResult]. The implementation is [TotalExtractor]. */
fun interface ReceiptExtractor {
    fun extract(document: OcrDocument): ReceiptResult
}
