package com.example.notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import com.example.notebook.ui.screen.dashboard.AppShell
import com.example.notebook.ui.theme.PastelNoteTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // מאפשר לקנבס למלא את כל המסך, כולל מתחת לשורת הסטטוס
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PastelNoteTheme {
                AppShell()
            }
        }
    }
}