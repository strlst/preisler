package dev.strlst.preisler.core

import kotlinx.serialization.Serializable

/**
 * OCR output in our own format, independent of any OCR engine.
 *
 * The Android app maps ML Kit results to this model. Test fixtures are
 * serialized instances of it.
 */
@Serializable
data class OcrDocument(
    val lines: List<OcrLine>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

@Serializable
data class OcrLine(
    val text: String,
    val box: BoundingBox,
)

/** Pixel coordinates in the source image; [top] < [bottom], [left] < [right]. */
@Serializable
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
    val width: Int get() = right - left
}
