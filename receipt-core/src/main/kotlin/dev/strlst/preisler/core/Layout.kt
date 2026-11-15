package dev.strlst.preisler.core

/** One printed row of a receipt: OCR lines that sit at the same height, left to right. */
data class Row(
    val lines: List<OcrLine>,
)

/**
 * Returns the lines in upright receipt coordinates.
 *
 * When most lines are much taller than wide, the photo was taken sideways with the text
 * running along +y and the top of the receipt at large x. The boxes are then rotated so
 * that rows can be assembled the same way as for an upright photo.
 */
fun uprightLines(document: OcrDocument): List<OcrLine> {
    val lines = document.lines
    val tall = lines.count { it.box.height > 2 * it.box.width }
    if (tall * 2 <= lines.size) return lines
    val w = document.imageWidth
    return lines.map { line ->
        val b = line.box
        line.copy(box = BoundingBox(left = b.top, top = w - b.right, right = b.bottom, bottom = w - b.left))
    }
}

/**
 * Groups lines into rows. ML Kit often returns the label (合計) and its amount (¥1,280) as
 * separate lines, so a line joins a row when each one's vertical center lies inside the other.
 */
fun assembleRows(lines: List<OcrLine>): List<Row> {
    class Building(
        val top: Int,
        val bottom: Int,
        val lines: MutableList<OcrLine>,
    )
    val rows = mutableListOf<Building>()
    for (line in lines.sortedBy { (it.box.top + it.box.bottom) / 2.0 }) {
        val b = line.box
        val cy = (b.top + b.bottom) / 2.0
        val row =
            rows.firstOrNull { r ->
                val rc = (r.top + r.bottom) / 2.0
                cy >= r.top && cy <= r.bottom && rc >= b.top && rc <= b.bottom
            }
        if (row != null) row.lines += line else rows += Building(b.top, b.bottom, mutableListOf(line))
    }
    return rows.map { r -> Row(r.lines.sortedBy { it.box.left }) }
}
