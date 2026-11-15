package dev.strlst.preisler.ui.scan

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.strlst.preisler.core.OcrDocument
import dev.strlst.preisler.core.ReceiptExtractor
import dev.strlst.preisler.core.ReceiptResult
import dev.strlst.preisler.ocr.OcrEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

sealed interface ScanState {
    data object Idle : ScanState

    data object Processing : ScanState

    data class Done(
        val document: OcrDocument,
        val result: ReceiptResult,
        val timings: Timings,
    ) : ScanState

    data class Failed(
        val message: String,
    ) : ScanState
}

/** How long each step took, shown in debug builds to tune capture resolution. */
data class Timings(
    val ocrMillis: Long,
    val extractMillis: Long,
)

/**
 * Drives capture → OCR → extraction. Shared by the scan and result screens.
 *
 * Privacy (AGENTS.md §3): the captured frame is closed here in every case, success or
 * failure, and only text is kept. Nothing is persisted.
 */
class ScanViewModel(
    private val ocr: OcrEngine,
    private val extractor: ReceiptExtractor,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun onImageCaptured(image: ImageProxy) {
        _state.value = ScanState.Processing
        viewModelScope.launch {
            _state.value =
                try {
                    val (document, ocrTime) = measureTimedValue { image.use { ocr.recognize(it) } }
                    val (result, extractTime) =
                        withContext(workDispatcher) { measureTimedValue { extractor.extract(document) } }
                    val timings = Timings(ocrTime.inWholeMilliseconds, extractTime.inWholeMilliseconds)
                    ScanState.Done(document, result, timings)
                } catch (e: Exception) {
                    ScanState.Failed(e.message ?: e.javaClass.simpleName)
                }
        }
    }

    fun onCaptureFailed(message: String) {
        _state.value = ScanState.Failed(message)
    }

    fun reset() {
        _state.value = ScanState.Idle
    }
}
