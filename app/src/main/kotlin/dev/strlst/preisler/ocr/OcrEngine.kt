package dev.strlst.preisler.ocr

import androidx.camera.core.ImageProxy
import dev.strlst.preisler.core.OcrDocument

/**
 * Recognizes text in a captured frame. Implementations must not store the image;
 * the caller ([dev.strlst.preisler.ui.scan.ScanViewModel]) closes it afterwards.
 */
fun interface OcrEngine {
    suspend fun recognize(image: ImageProxy): OcrDocument
}
