package dev.strlst.preisler.core

/**
 * Pipeline v1 (AGENTS.md §4): upright → rows → labels/kinds → amounts → scoring + cross-checks.
 *
 * Every positive amount (including the "4 was really ¥" alternative) is a candidate. Each
 * occurrence adds to its score by the kind of row it appears in. Cross-checks then add a
 * large bonus to values that equal お預り − お釣り or 小計 + plausible tax. When neither a
 * 合計 row nor a cross-check backs the winner, the largest amount is taken instead.
 */
object TotalExtractor : ReceiptExtractor {
    private class Item(
        val kinds: Set<RowKind>,
        val amount: Amount,
    )

    private const val TOTAL_WEIGHT = 10.0
    private const val PAYMENT_WEIGHT = 4.0
    private const val SUBTOTAL_WEIGHT = 3.0
    private const val SIDE_WEIGHT = 0.2
    private const val PLAIN_WEIGHT = 1.0
    private const val ALTERNATIVE_FACTOR = 0.8
    private const val YEN_BONUS = 0.5
    private const val CHECK_BONUS = 6.0

    override fun extract(document: OcrDocument): ReceiptResult {
        val rows = assembleRows(uprightLines(document))
        val items =
            rows.flatMap { row ->
                val kinds = classify(labelOf(row))
                row.lines.flatMap { line -> parseAmounts(line.text) }.map { Item(kinds, it) }
            }
        val rawText = rows.joinToString("\n") { row -> row.lines.joinToString(" ") { it.text } }
        val values = items.flatMap { it.amount.alternatives }.filter { it > 0 }.toSet()
        if (values.isEmpty()) return ReceiptResult(total = null, confidence = Confidence.NONE, rawText = rawText)

        val scores = values.associateWith { v -> items.sumOf { occurrenceScore(v, it) } }.toMutableMap()

        fun valuesOf(predicate: (Set<RowKind>) -> Boolean) =
            items.filter { predicate(it.kinds) }.flatMap { it.amount.alternatives }.toSet()
        val tenders =
            valuesOf { RowKind.TENDER in it || (RowKind.PAYMENT in it && RowKind.TOTAL !in it) }
        val changes = valuesOf { RowKind.CHANGE in it }
        val subtotals = valuesOf { RowKind.SUBTOTAL in it }
        val taxes = valuesOf { RowKind.TAX in it }

        val checked = mutableSetOf<Long>()
        for (v in values) {
            val paidMinusChange = tenders.any { t -> changes.any { c -> c >= 0 && t - c == v } }
            val subtotalPlusTax = subtotals.any { s -> taxes.any { x -> isPlausibleTax(x, s) && s + x == v } }
            if (paidMinusChange) scores[v] = scores.getValue(v) + CHECK_BONUS
            if (subtotalPlusTax) scores[v] = scores.getValue(v) + CHECK_BONUS
            if (paidMinusChange || subtotalPlusTax) checked += v
        }

        val best = values.maxWith(compareBy<Long>({ scores.getValue(it) }, { it }))
        val inTotalRow = items.any { RowKind.TOTAL in it.kinds && best in it.amount.alternatives }
        val (total, confidence) =
            when {
                inTotalRow && best in checked -> best to Confidence.HIGH
                inTotalRow || best in checked -> best to Confidence.MEDIUM
                else -> largestAmount(items, values) to Confidence.LOW
            }
        return ReceiptResult(total = total, confidence = confidence, rawText = rawText)
    }

    /**
     * Fallback when nothing names or confirms a total, e.g. meal tickets (食券) that list
     * no 合計 and no change: the total is the largest ¥ amount, or the largest amount at all.
     */
    private fun largestAmount(
        items: List<Item>,
        values: Set<Long>,
    ): Long =
        items
            .filter { it.amount.yenPrefixed }
            .map { it.amount.alternatives.first() }
            .filter { it > 0 }
            .maxOrNull() ?: values.max()

    private fun occurrenceScore(
        v: Long,
        item: Item,
    ): Double {
        val alternatives = item.amount.alternatives
        if (v !in alternatives) return 0.0
        val w = if (v == alternatives.first()) 1.0 else ALTERNATIVE_FACTOR
        val k = item.kinds
        val side = setOf(RowKind.TAX, RowKind.COUNT, RowKind.CHANGE, RowKind.TENDER)
        val base =
            when {
                RowKind.TOTAL in k -> TOTAL_WEIGHT
                RowKind.PAYMENT in k && RowKind.TENDER !in k && RowKind.CHANGE !in k -> PAYMENT_WEIGHT
                RowKind.SUBTOTAL in k && RowKind.TAX !in k -> SUBTOTAL_WEIGHT
                k.any { it in side } -> SIDE_WEIGHT
                else -> PLAIN_WEIGHT
            }
        return base * w + if (item.amount.yenPrefixed) YEN_BONUS else 0.0
    }

    /** Japanese consumption tax is 8% or 10%; allow rounding slack. */
    private fun isPlausibleTax(
        tax: Long,
        subtotal: Long,
    ) = tax > 0 && tax < subtotal * 0.11 + 2 && tax > subtotal * 0.07 - 2
}
