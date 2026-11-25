package dev.strlst.preisler.ui.result

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.strlst.preisler.BuildConfig
import dev.strlst.preisler.R
import dev.strlst.preisler.core.Category
import dev.strlst.preisler.core.Confidence
import dev.strlst.preisler.core.OcrDocument
import dev.strlst.preisler.core.PreislerJson
import dev.strlst.preisler.ui.scan.DateSource
import dev.strlst.preisler.ui.scan.ScanState
import dev.strlst.preisler.ui.scan.ScanViewModel
import dev.strlst.preisler.ui.theme.PreislerColors
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
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
            TotalCard(
                total = done?.result?.total,
                confidence = done?.result?.confidence ?: Confidence.NONE,
            )
            if (done != null) {
                DetailsCard(done, onDateChange = viewModel::setDate, onCategoryChange = viewModel::setCategory)
                RecognizedTextCard(done.document)
            }
            if (BuildConfig.DEBUG && done != null) {
                Text(
                    stringResource(
                        R.string.result_debug_timings,
                        done.timings.ocrMillis,
                        done.timings.extractMillis,
                        done.document.imageWidth,
                        done.document.imageHeight,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.result_retry))
                }
                if (BuildConfig.DEBUG && done != null) {
                    val context = LocalContext.current
                    OutlinedButton(onClick = { context.shareOcrJson(done.document) }, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null)
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
private fun DetailsCard(
    done: ScanState.Done,
    onDateChange: (LocalDate) -> Unit,
    onCategoryChange: (Category) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DetailRow(
                label = stringResource(R.string.result_date),
                value = done.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.JAPAN)),
                note = stringResource(dateNote(done.dateSource)),
                onClick = { pickingDate = true },
            )
            Box {
                DetailRow(
                    label = stringResource(R.string.result_category),
                    value = stringResource(categoryLabel(done.category)),
                    note =
                        stringResource(
                            if (done.categoryEdited) R.string.result_edited else R.string.result_detected,
                        ),
                    onClick = { pickingCategory = true },
                )
                DropdownMenu(expanded = pickingCategory, onDismissRequest = { pickingCategory = false }) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(stringResource(categoryLabel(category))) },
                            onClick = {
                                onCategoryChange(category)
                                pickingCategory = false
                            },
                        )
                    }
                }
            }
        }
    }
    if (pickingDate) {
        DatePickerSheet(
            initial = done.date,
            onPick = {
                onDateChange(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    note: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Text(note, style = MaterialTheme.typography.labelSmall, color = PreislerColors.Hanko)
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.result_edit, label))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC midnight milliseconds.
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun dateNote(source: DateSource) =
    when (source) {
        DateSource.RECEIPT -> R.string.result_date_from_receipt
        DateSource.SCAN -> R.string.result_date_scan
        DateSource.EDITED -> R.string.result_edited
    }

private fun categoryLabel(category: Category) =
    when (category) {
        Category.SUPERMARKET -> R.string.category_supermarket
        Category.KONBINI -> R.string.category_konbini
        Category.DRUGSTORE -> R.string.category_drugstore
        Category.RESTAURANT -> R.string.category_restaurant
        Category.FOOD_SHOP -> R.string.category_food_shop
        Category.RETAIL -> R.string.category_retail
        Category.LEISURE -> R.string.category_leisure
        Category.TRAVEL -> R.string.category_travel
        Category.OTHER -> R.string.category_other
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
