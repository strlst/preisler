package dev.strlst.preisler.core

import kotlinx.serialization.Serializable
import java.text.Normalizer

/** What kind of place a receipt comes from. The user can override it in the app. */
@Serializable
enum class Category {
    SUPERMARKET,
    KONBINI,
    DRUGSTORE,
    RESTAURANT,
    FOOD_SHOP,
    RETAIL,
    LEISURE,
    TRAVEL,
    OTHER,
}

/**
 * Categorizes a receipt (AGENTS.md §4) in three steps:
 * 1. a known chain in the first [HEADER_ROWS] or last [FOOTER_ROWS] rows, matched fuzzily
 *    because OCR mangles logos (`LAWB口N`, `セプン-イレフン`, `チイリーヤマザキ`);
 * 2. keyword cues anywhere on the receipt (定食, 乗車券, 観光…), most hits wins;
 * 3. only 10% tax and no reduced-rate (軽減) items → [Category.RETAIL]; otherwise [Category.OTHER].
 *
 * The matched store name is never returned or stored (privacy).
 */
fun findCategory(rows: List<Row>): Category {
    val texts = rows.map { it.text }
    val edges = (texts.take(HEADER_ROWS) + texts.takeLast(FOOTER_ROWS)).map(::fold)
    chains.firstOrNull { (_, name) -> edges.any { fuzzyContains(it, name) } }?.let { return it.first }

    val whole = fold(texts.joinToString(" "))
    val best = cues.maxWith(compareBy<Pair<Category, List<String>>> { (_, words) -> hits(whole, words) })
    if (hits(whole, best.second) > 0) return best.first

    val plain = Normalizer.normalize(texts.joinToString(" "), Normalizer.Form.NFKC)
    val rates = taxRate.findAll(plain.replace(" ", "")).map { it.groupValues[1] }.toSet()
    return if ("軽" !in plain && rates == setOf("10")) Category.RETAIL else Category.OTHER
}

private const val HEADER_ROWS = 8
private const val FOOTER_ROWS = 5

// Declared before the tables below: they call fold() while this file initializes.
private val taxRate = Regex("""(8|10)(?:\.0+)?\s*%""")
private val separators = Regex("""[\s\-‐−–·・.,:;'"()\[\]「」【】]""")

// Names are space-separated; none contains a space.
private val chains: List<Pair<Category, String>> =
    listOf(
        Category.KONBINI to "LAWSON ローソン セブンイレブン 7ELEVEN ファミリーマート FAMILYMART ミニストップ デイリーヤマザキ NEWDAYS セイコーマート",
        Category.SUPERMARKET to
            "SEIYU 西友 イオン AEON ÆON イトーヨーカドー マルエツ サミット いなげや 東急ストア ヤオコー マックスバリュ " +
            "まいばすけっと 業務スーパー オーケーストア",
        Category.DRUGSTORE to
            "サンドラッグ コクミン KAMEGAYA カメガヤ FITCARE マツモトキヨシ ウエルシア スギ薬局 ツルハ ココカラファイン " +
            "クリエイトSD トモズ ダイコクドラッグ",
        Category.RESTAURANT to "すき家 松屋 吉野家 サイゼリヤ ガスト マクドナルド スターバックス ドトール タリーズ コメダ SALVATORE",
        Category.FOOD_SHOP to "コージーコーナー 九州屋 シャトレーゼ 不二家",
        Category.RETAIL to "ABCMART ドンキホーテ ニトリ NITORI ユニクロ UNIQLO 無印良品 ダイソー ヨドバシ ビックカメラ ロフト",
    ).flatMap { (category, names) -> names.split(" ").map { category to fold(it) } }

// Order breaks ties: the first category with the most hits wins.
private val cues: List<Pair<Category, List<String>>> =
    listOf(
        Category.TRAVEL to "乗車券 特急 指定席 運賃 在来線 新幹線 定期券 フリーパス タクシー 駐車 パーキング 宿泊 ホテル 給油 ガソリン",
        Category.DRUGSTORE to "ドラッグ 薬局 調剤 医薬品",
        Category.RESTAURANT to "定食 ランチ ディナー テーブ 人数 伝票 サービス料 ごはん みそ汁 (並) 大盛 お通し",
        Category.FOOD_SHOP to "ベーカリー 洋菓子 和菓子 青果 鮮魚 精肉",
        Category.LEISURE to "観光 パーク 入場 交流館 美術館 博物館 水族館 動物園 遊園地 映画",
    ).map { (category, words) -> category to words.split(" ").map(::fold) }

/** Folds text for matching: NFKC, upper case, 口→O, no separators, dakuten/handakuten dropped (ガ→カ, プ→フ). */
internal fun fold(text: String): String {
    val upper = Normalizer.normalize(text, Normalizer.Form.NFKC).uppercase().replace('口', 'O')
    val bare = Normalizer.normalize(upper.replace(separators, ""), Normalizer.Form.NFD)
    return Normalizer.normalize(bare.replace("゙", "").replace("゚", ""), Normalizer.Form.NFC)
}

private fun hits(
    text: String,
    words: List<String>,
): Int = words.sumOf { word -> countOccurrences(text, word) }

private fun countOccurrences(
    text: String,
    word: String,
): Int {
    var count = 0
    var from = text.indexOf(word)
    while (from >= 0) {
        count++
        from = text.indexOf(word, from + word.length)
    }
    return count
}

/** True when [text] contains [name] with at most 0 (≤ 3 chars), 1 (≤ 7) or 2 edits. */
internal fun fuzzyContains(
    text: String,
    name: String,
): Boolean {
    if (name in text) return true
    val n = name.length
    val k =
        when {
            n <= 3 -> 0
            n <= 7 -> 1
            else -> 2
        }
    if (k == 0) return false
    for (w in maxOf(1, n - k)..(n + k)) {
        for (i in 0..(text.length - w)) {
            if (editDistance(text.substring(i, i + w), name) <= k) return true
        }
    }
    return false
}

private fun editDistance(
    a: String,
    b: String,
): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            val substitution = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, substitution)
        }
        prev = cur
    }
    return prev[b.length]
}
