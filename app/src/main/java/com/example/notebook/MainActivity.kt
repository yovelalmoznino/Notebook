package com.example.notebook

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.util.Log // נוסף לצורך אבחון
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import com.example.notebook.ui.screen.dashboard.AppShell
import com.example.notebook.ui.theme.PastelNoteTheme
import kotlinx.coroutines.flow.MutableSharedFlow // ייבוא קריטי שפתר את השגיאה בתמונה

// אובייקט גלובלי לניהול אירועי העט - חייב להישאר מחוץ ל-Class
object StylusButtonManager {
    // SharedFlow מאפשר לשלוח אירוע "קליק" בודד ללא שמירת מצב קבוע
    val buttonClicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // מאפשר לקנבס למלא את כל המסך
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PastelNoteTheme {
                AppShell()
            }
        }
    }

    /**
     * התיקון הקריטי עבור לנובו:
     * dispatchKeyEvent תופס את האירוע לפני כל רכיב אחר.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // לפי הלוגים שלך, קוד 600 הוא הכפתור בעט הלנובו
        if (event.keyCode == 600 || event.keyCode == 601) {
            // אנחנו מגיבים רק לשחרור הכפתור (ACTION_UP) כדי לבצע Toggle פעם אחת בלבד
            if (event.action == KeyEvent.ACTION_UP) {
                Log.d("StylusButton", "Button 600 released - triggering toggle")
                StylusButtonManager.buttonClicks.tryEmit(Unit)
            }
            // החזרת true מונעת מהמערכת של לנובו להציג את התפריט המובנה שלה
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}