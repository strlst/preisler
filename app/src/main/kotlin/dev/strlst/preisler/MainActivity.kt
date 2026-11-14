package dev.strlst.preisler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.strlst.preisler.ui.PreislerApp
import dev.strlst.preisler.ui.theme.PreislerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PreislerTheme {
                PreislerApp()
            }
        }
    }
}
