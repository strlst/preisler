package dev.strlst.preisler.core

/** Turns OCR output into a [ReceiptResult]. The implementation is [ReceiptReader]. */
fun interface ReceiptExtractor {
    fun extract(document: OcrDocument): ReceiptResult
}
