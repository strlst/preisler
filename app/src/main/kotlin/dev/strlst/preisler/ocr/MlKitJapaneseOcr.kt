package dev.strlst.preisler.ocr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import dev.strlst.preisler.core.BoundingBox
import dev.strlst.preisler.core.OcrDocument
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR with ML Kit's bundled Japanese model (works offline). */
class MlKitJapaneseOcr : OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    @OptIn(ExperimentalGetImage::class)
    override suspend fun recognize(image: ImageProxy): OcrDocument {
        val mediaImage = checkNotNull(image.image) { "Captured frame has no image data" }
        val rotation = image.imageInfo.rotationDegrees
        val text = recognizer.process(InputImage.fromMediaImage(mediaImage, rotation)).await()
        val lines =
            text.textBlocks
                .asSequence()
                .flatMap { it.lines.asSequence() }
                .map { line ->
                    RawLine(
                        text = line.text,
                        box = line.boundingBox?.let { BoundingBox(it.left, it.top, it.right, it.bottom) },
                    )
                }
        return buildOcrDocument(lines, image.width, image.height, rotation)
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
