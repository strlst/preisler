package dev.strlst.preisler.core

import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate

private val kanjiDate = Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
private val reiwaDate = Regex("""(?:令和|R)\s*(\d{1,2}|元)\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
private val numericDate = Regex("""(?<!\d)(20\d{2})\s*[/.\-]\s*(\d{1,2})\s*[/.\-]\s*(\d{1,2})(?!\d)""")

// Meal-ticket machines print "YY.MM.DD HH:MM"; OCR turns it into "2611 12 12:10" or "261 10,9 12;11".
private val compactDateBeforeTime = Regex("""([0-9][0-9 ,.]{4,9})\s+\d{1,2}[:;]\d{2}""")

private const val REIWA_YEAR_ZERO = 2018
private const val CENTURY = 2000

/**
 * The receipt date as ISO-8601 (`2026-11-14`), or null when none is legible.
 *
 * Rows are searched top to bottom; the first real calendar date wins. Years must be printed:
 * validity ranges such as "10月2日から" on tickets are ignored on purpose.
 */
fun findDate(rows: List<Row>): String? =
    rows.firstNotNullOfOrNull { row -> dateIn(Normalizer.normalize(row.text, Normalizer.Form.NFKC)) }?.toString()

private fun dateIn(text: String): LocalDate? {
    kanjiDate.findAll(text).firstNotNullOfOrNull { m -> dateOf(m.int(1), m.int(2), m.int(3)) }?.let { return it }
    reiwaDate
        .findAll(text)
        .firstNotNullOfOrNull { m ->
            val year = m.groupValues[1].let { if (it == "元") 1 else it.toInt() }
            dateOf(REIWA_YEAR_ZERO + year, m.int(2), m.int(3))
        }?.let { return it }
    numericDate.findAll(text).firstNotNullOfOrNull { m -> dateOf(m.int(1), m.int(2), m.int(3)) }?.let { return it }
    val compact = compactDateBeforeTime.matchEntire(text.trim()) ?: return null
    val digits = compact.groupValues[1].filter { it.isDigit() }
    if (digits.length != 6) return null
    return dateOf(CENTURY + digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4).toInt())
}

private fun MatchResult.int(group: Int) = groupValues[group].toInt()

private fun dateOf(
    year: Int,
    month: Int,
    day: Int,
): LocalDate? =
    try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }
