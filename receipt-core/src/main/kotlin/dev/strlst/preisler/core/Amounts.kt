package dev.strlst.preisler.core

import java.text.Normalizer

/**
 * An amount found in one OCR line.
 *
 * [alternatives] holds the plain reading first. When OCR may have turned the ¥ sign into a
 * leading 4 (¥360 → "4360"), the reading without it follows. Scoring decides which is right.
 */
data class Amount(
    val alternatives: List<Long>,
    val yenPrefixed: Boolean,
)

/** NFKC (full-width → ASCII) plus the yen sign variants OCR produces. */
fun normalizeText(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFKC)
        .replace('￥', '¥')
        .replace('\\', '¥')

private const val MAX_DIGITS = 7
private const val TRAILING_MARKS = "()内外軽※*XxC点個.、, "

private val yenAmount = Regex("""(?:¥|(?<![A-Za-z])Y|^\*|^-¥)\s*([0-9OoDlI][0-9OoDlI ,.]*)""")
private val bareNumber = Regex("""[0-9OoD][0-9OoDlI ,.]*""")
private val grouped = Regex("""\d{1,3}([.,]\d{3})+""")
private val digitsOnly = Regex("""\d+""")
private val leadingNumber = Regex("""\d{1,3}(,\d{3})+|\d+""")
private val validAfterYen = Regex("""[1-9]\d{0,2}(,\d{3})*|[1-9]\d*""")

/**
 * Finds the amounts in one OCR line. Only two shapes count as money:
 * a ¥-prefixed number anywhere in the line, or a line that is nothing but a number
 * (plus markers such as `)`, 内, 軽, ※). This keeps dates, phone numbers and IDs out.
 */
fun parseAmounts(text: String): List<Amount> {
    val t = normalizeText(text).trim()
    val yen = yenAmount.findAll(t).mapNotNull { m -> cleanNumber(m.groupValues[1]) }.toList()
    if (yen.isNotEmpty()) return yen.map { Amount(listOf(it), yenPrefixed = true) }

    val core = t.trim { it in TRAILING_MARKS }.trimStart('(').trim()
    if (!bareNumber.matches(core)) return emptyList()
    val raw = fixLetters(core.replace(" ", ""))
    if (raw.startsWith("0") && raw != "0") return emptyList()
    val value = cleanNumber(raw) ?: return emptyList()
    val alternatives = mutableListOf(value)
    val digitCount = raw.count { it.isDigit() }
    if (raw.startsWith("4") && digitCount >= 3) {
        val rest = raw.substring(1)
        if (validAfterYen.matches(rest)) alternatives += rest.replace(",", "").toLong()
    }
    return listOf(Amount(alternatives, yenPrefixed = false))
}

private fun fixLetters(s: String): String =
    s
        .replace('O', '0')
        .replace('o', '0')
        .replace('D', '0')
        .replace('l', '1')
        .replace('I', '1')

private fun cleanNumber(input: String): Long? {
    val s = fixLetters(input).replace(" ", "").trimEnd(',', '.')
    if (s.isEmpty() || !s[0].isDigit()) return null
    val digits =
        when {
            grouped.matches(s) -> s.replace(",", "").replace(".", "")
            digitsOnly.matches(s) -> s
            else -> leadingNumber.find(s)?.value?.replace(",", "") ?: return null
        }
    if (digits.length > MAX_DIGITS) return null
    return digits.toLong()
}
