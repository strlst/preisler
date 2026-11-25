package dev.strlst.preisler.core

/**
 * The extraction pipeline (AGENTS.md §4): OCR lines → upright rows → total, date, category.
 * Rows are built once and shared by the three finders.
 */
object ReceiptReader : ReceiptExtractor {
    override fun extract(document: OcrDocument): ReceiptResult {
        val rows = assembleRows(uprightLines(document))
        val total = findTotal(rows)
        return ReceiptResult(
            total = total.total,
            confidence = total.confidence,
            date = findDate(rows),
            category = findCategory(rows),
            rawText = rows.joinToString("\n") { it.text },
        )
    }
}
