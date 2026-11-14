package dev.strlst.preisler.core

import kotlinx.serialization.Serializable

/**
 * What the app extracts from a receipt. This is also the payload for the
 * future LAN export, so changes must bump [schemaVersion].
 */
@Serializable
data class ReceiptResult(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Total price in yen, or null if none could be determined. */
    val total: Long?,
    val confidence: Confidence,
    val subtotal: Long? = null,
    val taxLines: List<TaxLine> = emptyList(),
    /** ISO-8601 date, filled in a later goal. */
    val date: String? = null,
    val storeName: String? = null,
    /** Normalized OCR text, kept for debugging and export. */
    val rawText: String = "",
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
enum class Confidence { HIGH, MEDIUM, LOW, NONE }

@Serializable
data class TaxLine(
    /** Tax rate in percent, e.g. 8 or 10. */
    val ratePercent: Int,
    /** Taxable amount (対象額) in yen, if printed. */
    val base: Long? = null,
    /** Tax amount in yen, if printed. */
    val tax: Long? = null,
)
