package com.example.notebook

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PastelNoteTheme {
                AppShell()
            }
        }
    }

    /**
     * תופס כפתורי עט לפני כל רכיב אחר.
     * תומך: לנובו (keyCode 600/601), סמסונג S Pen, ועטים כלליים.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (StylusButtonManager.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * תמיכה בעטי סמסונג S Pen ועטי Lenovo Precision Pen
     * דרך MotionEvent (BUTTON_PRESS).
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (StylusButtonManager.handleMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }
}