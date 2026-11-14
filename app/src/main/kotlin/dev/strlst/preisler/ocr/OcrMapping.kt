package dev.strlst.preisler.ocr

import dev.strlst.preisler.core.BoundingBox
import dev.strlst.preisler.core.OcrDocument
import dev.strlst.preisler.core.OcrLine

/** One recognized line, engine-agnostic, before it becomes an [OcrLine]. */
data class RawLine(
    val text: String,
    val box: BoundingBox?,
)

/**
 * Builds an [OcrDocument] in upright coordinates.
 *
 * ML Kit reports boxes relative to the image after applying [rotationDegrees], so the
 * document's width and height are swapped for 90° and 270°. Lines without text or
 * without a box are dropped, because row assembly needs geometry.
 */
fun buildOcrDocument(
    lines: Sequence<RawLine>,
    sensorWidth: Int,
    sensorHeight: Int,
    rotationDegrees: Int,
): OcrDocument {
    val swap = rotationDegrees % 180 != 0
    return OcrDocument(
        lines =
            lines
                .filter { it.text.isNotBlank() && it.box != null }
                .map { OcrLine(text = it.text.trim(), box = it.box!!) }
                .toList(),
        imageWidth = if (swap) sensorHeight else sensorWidth,
        imageHeight = if (swap) sensorWidth else sensorHeight,
    )
}
