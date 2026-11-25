package dev.strlst.preisler.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTest {
    private fun categoryOf(vararg rowTexts: String) =
        findCategory(rowTexts.mapIndexed { i, t -> Row(listOf(OcrLine(t, BoundingBox(0, i * 50, 100, i * 50 + 40)))) })

    @Test
    fun chainsSurviveOcrMisreadings() {
        assertEquals(Category.KONBINI, categoryOf("LAWB口N", "2026年11月14日"))
        assertEquals(Category.KONBINI, categoryOf("予セプン-イレフン"))
        assertEquals(Category.KONBINI, categoryOf("チイリーヤマザキ"))
        assertEquals(Category.SUPERMARKET, categoryOf("ÆON", "イオン駒岡店"))
        assertEquals(Category.DRUGSTORE, categoryOf("サンドラッグ溝の口店"))
        assertEquals(Category.RETAIL, categoryOf("ABC-MART"))
        assertEquals(Category.FOOD_SHOP, categoryOf("銀座コージーコーナー"))
    }

    @Test
    fun chainsOnlyCountInTheHeaderOrFooter() {
        // A product called ライオン in the middle of a long receipt must not make it an AEON (イオン) receipt.
        val middle = List(10) { "商品$it" }
        val rows = listOf("ABCDEF商店") + middle + listOf("ライオン 歯みがき") + middle + listOf("合計")
        assertEquals(Category.OTHER, categoryOf(*rows.toTypedArray()))
    }

    @Test
    fun cuesDecideForUnknownStores() {
        assertEquals(Category.TRAVEL, categoryOf("北海道フリーバス", "(乗車券:自由座特急)"))
        assertEquals(Category.TRAVEL, categoryOf("〇〇タクシー", "運賃 ¥1,200"))
        assertEquals(Category.RESTAURANT, categoryOf("テーブル:B", "人数:1"))
        assertEquals(Category.RESTAURANT, categoryOf("和風ハンバーグ定食", "ごはん(並)"))
        assertEquals(Category.DRUGSTORE, categoryOf("〇〇薬局", "調剤"))
        assertEquals(Category.LEISURE, categoryOf("足利観光交流館"))
        assertEquals(Category.FOOD_SHOP, categoryOf("〇〇ベーカリー"))
    }

    @Test
    fun taxRatesAsLastResort() {
        assertEquals(Category.RETAIL, categoryOf("タケヤ", "外税対象 10. 0% Y2, 800"))
        assertEquals(Category.OTHER, categoryOf("タケヤ", "外税対象 8% ¥1,000", "軽減税率対象"))
        assertEquals(Category.OTHER, categoryOf("ありがとうございました"))
    }

    @Test
    fun foldingAndFuzzyMatching() {
        assertEquals("セフンイレフン", fold("セブン-イレブン"))
        assertEquals("LAWSON", fold("lawson"))
        assertTrue(fuzzyContains(fold("LAWB口N"), fold("LAWSON")))
        assertFalse(fuzzyContains(fold("ニトロ"), fold("ニトリ")))
    }
}
