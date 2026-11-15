package dev.strlst.preisler

import android.app.Application
import dev.strlst.preisler.core.ReceiptExtractor
import dev.strlst.preisler.core.TotalExtractor
import dev.strlst.preisler.ocr.MlKitJapaneseOcr
import dev.strlst.preisler.ocr.OcrEngine

class PreislerApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

/** Manual dependency wiring (AGENTS.md §2). */
class AppContainer {
    val ocrEngine: OcrEngine by lazy { MlKitJapaneseOcr() }
    val extractor: ReceiptExtractor = TotalExtractor
}
