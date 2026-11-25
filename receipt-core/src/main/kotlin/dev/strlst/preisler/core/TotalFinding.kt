package dev.strlst.preisler.core

import kotlin.math.ceil
import kotlin.math.floor

/** The total and how sure the rules are about it. */
data class TotalFinding(
    val total: Long?,
    val confidence: Confidence,
)

/**
 * Finds the total (AGENTS.md §4): labels/kinds → amounts → scoring + cross-checks.
 *
 * Every positive amount (including the "4 was really ¥" alternative) is a candidate. Each
 * occurrence adds to its score by the kind of row it appears in. Cross-checks then add a
 * large bonus to values that equal お預り − お釣り or 小計 + tax. When neither a 合計 row nor
 * a cross-check backs the winner, the largest amount is taken instead.
 */
fun findTotal(rows: List<Row>): TotalFinding = TotalRules.find(rows)

private object TotalRules {
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
    private val TAX_RATES = listOf(0.08, 0.10)
    private val sideKinds = setOf(RowKind.TAX, RowKind.COUNT, RowKind.CHANGE, RowKind.TENDER)

    fun find(rows: List<Row>): TotalFinding {
        val items =
            rows.flatMap { row ->
                val amounts = row.lines.map { parseAmounts(it.text) }
                val kinds = classify(labelOf(row, amounts))
                amounts.flatten().map { Item(kinds, it) }
            }
        val allValues = items.flatMap { it.amount.alternatives }.toSet()
        val values = allValues.filter { it > 0 }.toSet()
        if (values.isEmpty()) return TotalFinding(total = null, confidence = Confidence.NONE)

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
            val subtotalPlusTax =
                subtotals.any { s -> allValues.any { x -> s + x == v && isPlausibleTax(x, s, labeled = x in taxes) } }
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
        return TotalFinding(total, confidence)
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
        val base =
            when {
                RowKind.TOTAL in k -> TOTAL_WEIGHT
                RowKind.PAYMENT in k && RowKind.TENDER !in k && RowKind.CHANGE !in k -> PAYMENT_WEIGHT
                RowKind.SUBTOTAL in k && RowKind.TAX !in k -> SUBTOTAL_WEIGHT
                k.any { it in sideKinds } -> SIDE_WEIGHT
                else -> PLAIN_WEIGHT
            }
        return base * w + if (item.amount.yenPrefixed) YEN_BONUS else 0.0
    }

    /**
     * Japanese consumption tax is 8% or 10%. A value from a labeled tax row may be 7–11% of the
     * subtotal (rounding, discounts). Any other value must be exactly 8% or 10% of it, rounded
     * down or up, e.g. an unlabeled "11" under 小計 140 (fixture 0021).
     */
    private fun isPlausibleTax(
        tax: Long,
        subtotal: Long,
        labeled: Boolean,
    ): Boolean {
        if (tax <= 0) return false
        if (labeled && tax < subtotal * 0.11 + 2 && tax > subtotal * 0.07 - 2) return true
        return TAX_RATES.any { rate ->
            val exact = subtotal * rate
            tax == floor(exact).toLong() || tax == ceil(exact).toLong()
        }
    }
}
