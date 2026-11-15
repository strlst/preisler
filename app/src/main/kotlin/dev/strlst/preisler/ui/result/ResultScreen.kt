package dev.strlst.preisler.ui.result

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.strlst.preisler.BuildConfig
import dev.strlst.preisler.R
import dev.strlst.preisler.core.Confidence
import dev.strlst.preisler.core.OcrDocument
import dev.strlst.preisler.core.PreislerJson
import dev.strlst.preisler.ui.scan.ScanState
import dev.strlst.preisler.ui.scan.ScanViewModel
import dev.strlst.preisler.ui.theme.PreislerColors
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ScanViewModel,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done = state as? ScanState.Done
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TotalCard(total = done?.result?.total, confidence = done?.result?.confidence ?: Confidence.NONE)
            if (done != null) RecognizedTextCard(done.document)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.result_retry))
                }
                if (BuildConfig.DEBUG && done != null) {
                    val context = LocalContext.current
                    OutlinedButton(onClick = { context.shareOcrJson(done.document) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.result_export_ocr))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TotalCard(
    total: Long?,
    confidence: Confidence,
) {
    Surface(
        color = PreislerColors.Indigo,
        contentColor = PreislerColors.Paper,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("合計", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (total != null) {
                Text(
                    "¥" + NumberFormat.getIntegerInstance(Locale.JAPAN).format(total),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(confidenceLabel(confidence)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (confidence == Confidence.HIGH) PreislerColors.Paper else PreislerColors.Hanko,
                )
            } else {
                Text(
                    stringResource(R.string.result_total_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PreislerColors.Paper.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun confidenceLabel(confidence: Confidence) =
    when (confidence) {
        Confidence.HIGH -> R.string.result_confidence_high
        Confidence.MEDIUM -> R.string.result_confidence_medium
        Confidence.LOW, Confidence.NONE -> R.string.result_confidence_low
    }

@Composable
private fun RecognizedTextCard(document: OcrDocument) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.result_recognized_text, document.lines.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    document.lines
                        .joinToString("\n") { it.text }
                        .ifEmpty { stringResource(R.string.result_no_text) },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Debug builds only: share the OCR text as JSON (never the picture) to create test fixtures. */
private fun Context.shareOcrJson(document: OcrDocument) {
    val json = PreislerJson.encodeToString(OcrDocument.serializer(), document)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "preisler OCR export")
            putExtra(Intent.EXTRA_TEXT, json)
        }
    startActivity(Intent.createChooser(send, getString(R.string.result_export_ocr)))
}
