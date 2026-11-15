package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountsTest {
    private fun parse(text: String) = parseAmounts(text).map { it.alternatives to it.yenPrefixed }

    @Test
    fun yenPrefixedAmounts() {
        assertEquals(listOf(listOf(1280L) to true), parse("¥1,280"))
        assertEquals(listOf(listOf(1280L) to true), parse("￥１，２８０"))
        assertEquals(listOf(listOf(2600L) to true), parse("ビーフシチューランチ ¥2,600内"))
        assertEquals(listOf(listOf(3080L) to true), parse("Y3,080"))
        assertEquals(listOf(listOf(398L) to true), parse("*398"))
    }

    @Test
    fun ocrNoiseInsideNumbers() {
        assertEquals(listOf(listOf(1000L) to true), parse("¥1,00 O"))
        assertEquals(listOf(listOf(5050L) to true), parse("¥5,0 5 0"))
        assertEquals(listOf(listOf(10000L) to true), parse("¥10,0 O0"))
        assertEquals(listOf(listOf(290L) to false), parse("29O"))
    }

    @Test
    fun leadingFourMayBeAYenSign() {
        assertEquals(listOf(listOf(4360L, 360L) to false), parse("4360"))
        assertEquals(listOf(listOf(41300L, 1300L) to false), parse("41,300"))
        // "4, 050" is really 4,050: dropping the 4 leaves no valid number.
        assertEquals(listOf(listOf(4050L) to false), parse("4, 050)"))
    }

    @Test
    fun bareNumbersWithMarkers() {
        assertEquals(listOf(listOf(300L) to false), parse("300軽"))
        assertEquals(listOf(listOf(200L) to false), parse("200※"))
        assertEquals(listOf(listOf(39L) to false), parse("39)"))
    }

    @Test
    fun ignoresDatesPhonesAndIds() {
        assertEquals(emptyList<Any>(), parse("2026年11月14日 (土)11:35 No:0001"))
        assertEquals(emptyList<Any>(), parse("TEL:0284-44-0024"))
        assertEquals(emptyList<Any>(), parse("2910000030069"))
        assertEquals(emptyList<Any>(), parse("0024"))
        assertEquals(emptyList<Any>(), parse("2592-036910%"))
    }
}
