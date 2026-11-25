package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DateTest {
    private fun dateOf(vararg rowTexts: String) =
        findDate(rowTexts.mapIndexed { i, t -> Row(listOf(OcrLine(t, BoundingBox(0, i * 50, 100, i * 50 + 40)))) })

    @Test
    fun printedFormats() {
        assertEquals("2026-11-14", dateOf("2026年11月14日 (土)11:35 No:0001"))
        assertEquals("2026-11-14", dateOf("2026年11月 14日"))
        assertEquals("2026-10-03", dateOf("２０２６年１０月０３日（土）"))
        assertEquals("2026-09-07", dateOf("2026/ 9/ 7(月) 12:19"))
        assertEquals("2026-11-01", dateOf("2026-11-01 16:02:59"))
        assertEquals("2026-11-25", dateOf("令和8年11月25日"))
        assertEquals("2019-05-01", dateOf("令和元年5月1日"))
    }

    @Test
    fun mealTicketCompactDates() {
        assertEquals("2026-11-12", dateOf("2611 12 12:10"))
        assertEquals("2026-11-09", dateOf("261 10,9 12;11"))
    }

    @Test
    fun firstRealDateFromTheTopWins() {
        assertEquals("2026-07-24", dateOf("TEL 045-510-3951", "2026年07月24日 (金)20:48", "2026年07月25日"))
        assertEquals("2026-03-01", dateOf("2026年02月30日", "2026年03月01日"))
    }

    @Test
    fun noYearOrNoDateMeansNull() {
        assertEquals(null, dateOf("110月-2日から", "¥|19月-8日有効"))
        assertEquals(null, dateOf("TEL:0284-44-0024", "2910000030069"))
    }
}
