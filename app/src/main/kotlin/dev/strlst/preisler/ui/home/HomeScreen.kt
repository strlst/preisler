package dev.strlst.preisler.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.strlst.preisler.R
import dev.strlst.preisler.ui.theme.PreislerColors
import dev.strlst.preisler.ui.theme.PreislerTheme

const val SCAN_BUTTON_TAG = "home_scan_button"

@Composable
fun HomeScreen(onScanClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(PreislerColors.Indigo, PreislerColors.IndigoDeep))),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(top = 48.dp)) {
                Text(
                    text = "¥",
                    color = PreislerColors.Hanko,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.app_name),
                    color = PreislerColors.Paper,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_tagline),
                    color = PreislerColors.Paper.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_tagline_ja),
                    color = PreislerColors.Paper.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            ReceiptCard()

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onScanClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag(SCAN_BUTTON_TAG),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PreislerColors.Hanko,
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(painterResource(R.drawable.ic_scan), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_scan_button), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = PreislerColors.Paper.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_privacy_note),
                        color = PreislerColors.Paper.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** A decorative mini receipt showing what the app looks for. */
@Composable
private fun ReceiptCard() {
    Surface(
        color = PreislerColors.Paper,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            ReceiptRow("小計", "¥1,185", emphasized = false)
            ReceiptRow("消費税 (8%)", "¥95", emphasized = false)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PreislerColors.Indigo.copy(alpha = 0.2f)),
            )
            Spacer(Modifier.height(8.dp))
            ReceiptRow("合計", "¥1,280", emphasized = true)
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    amount: String,
    emphasized: Boolean,
) {
    val color = if (emphasized) PreislerColors.Hanko else PreislerColors.IndigoDeep.copy(alpha = 0.7f)
    val style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = color, style = style, fontWeight = if (emphasized) FontWeight.Bold else null)
        Text(amount, color = color, style = style, fontWeight = if (emphasized) FontWeight.Bold else null)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    PreislerTheme { HomeScreen(onScanClick = {}) }
}
