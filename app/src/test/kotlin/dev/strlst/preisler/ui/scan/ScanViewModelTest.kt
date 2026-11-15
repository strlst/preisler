package dev.strlst.preisler.ui.scan

import androidx.camera.core.ImageProxy
import dev.strlst.preisler.core.BoundingBox
import dev.strlst.preisler.core.OcrDocument
import dev.strlst.preisler.core.OcrLine
import dev.strlst.preisler.core.TotalExtractor
import dev.strlst.preisler.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val document = OcrDocument(listOf(OcrLine("合計 ¥1,280", BoundingBox(0, 0, 10, 10))))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A fake frame that only records whether it was closed. */
    private class FakeFrame {
        var closed = false
        val proxy: ImageProxy =
            Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, m, _ ->
                if (m.name == "close") closed = true
                null
            } as ImageProxy
    }

    private fun viewModel(engine: OcrEngine) = ScanViewModel(engine, TotalExtractor, dispatcher)

    @Test
    fun successProducesDoneAndClosesFrame() {
        val frame = FakeFrame()
        val vm = viewModel { document }

        vm.onImageCaptured(frame.proxy)

        val state = vm.state.value
        assertTrue(state is ScanState.Done)
        assertEquals(document, (state as ScanState.Done).document)
        assertEquals(1280L, state.result.total)
        assertTrue("timings are measured", state.timings.ocrMillis >= 0 && state.timings.extractMillis >= 0)
        assertTrue("frame must be closed after OCR", frame.closed)
    }

    @Test
    fun ocrFailureProducesFailedAndStillClosesFrame() {
        val frame = FakeFrame()
        val vm = viewModel { error("model not ready") }

        vm.onImageCaptured(frame.proxy)

        assertEquals(ScanState.Failed("model not ready"), vm.state.value)
        assertTrue("frame must be closed even when OCR fails", frame.closed)
    }

    @Test
    fun resetReturnsToIdle() {
        val vm = viewModel { document }
        vm.onCaptureFailed("camera busy")
        assertEquals(ScanState.Failed("camera busy"), vm.state.value)

        vm.reset()
        assertEquals(ScanState.Idle, vm.state.value)
    }
}
