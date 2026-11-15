package dev.strlst.preisler.core

/** What a receipt row is about, judged from its label text. A row can have several kinds. */
enum class RowKind { TOTAL, SUBTOTAL, TAX, TENDER, CHANGE, PAYMENT, COUNT }

private val tender = Regex("預|現金")
private val change = Regex("釣|お的|おっり|お釣")
private val tax = Regex("税|対象")
private val count = Regex("""点数|買上点|\d点|個""")
private val payment = Regex("現計|クレジット|クレシ|取扱|デビット|電子マネー|PayPay|Suica")

// Includes common OCR misreadings of 合計 (合十, 舎計).
private val total =
    Regex(
        listOf(
            "合計",
            "合十",
            "舎計",
            "総計",
            "お会計",
            "ご請求",
            "請求額",
            "領収金額",
            "支払金額",
            "お支払",
            "買上金額",
            "^計$",
        ).joinToString("|"),
    )

private val whitespace = Regex("""\s+""")
private val amountText = Regex("""[¥Y]?\s*[0-9][0-9 ,.]*""")

/**
 * The row's words with amounts and whitespace removed, e.g. "合 計 ¥1,280" → "合計".
 * [amounts] holds [parseAmounts] per line; pass it when already computed.
 */
fun labelOf(
    row: Row,
    amounts: List<List<Amount>> = row.lines.map { parseAmounts(it.text) },
): String =
    row.lines
        .zip(amounts)
        .joinToString("") { (line, found) ->
            val text = normalizeText(line.text)
            if (found.isEmpty()) text else text.replace(amountText, " ")
        }.replace(whitespace, "")

fun classify(label: String): Set<RowKind> {
    val kinds = mutableSetOf<RowKind>()
    if (tender.containsMatchIn(label)) kinds += RowKind.TENDER
    // OCR sometimes keeps only the お of お釣り.
    if (change.containsMatchIn(label) || label == "お") kinds += RowKind.CHANGE
    if ("小計" in label) kinds += RowKind.SUBTOTAL
    if (tax.containsMatchIn(label)) kinds += RowKind.TAX
    if (count.containsMatchIn(label)) kinds += RowKind.COUNT
    if (payment.containsMatchIn(label)) kinds += RowKind.PAYMENT
    val excluded = kinds.any { it == RowKind.TENDER || it == RowKind.COUNT || it == RowKind.TAX }
    if (total.containsMatchIn(label) && !excluded) kinds += RowKind.TOTAL
    return kinds
}
