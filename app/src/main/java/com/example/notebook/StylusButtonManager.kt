package com.example.notebook

import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.notebook.data.model.StylusAction

object StylusButtonManager {

    // כפתור עיקרי (לנובו 600, סמסונג primary, ועטים כלליים)
    private val _button1Clicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val button1Clicks = _button1Clicks.asSharedFlow()

    // כפתור משני (לנובו 601, סמסונג secondary)
    private val _button2Clicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val button2Clicks = _button2Clicks.asSharedFlow()

    // תאימות לאחור עם הקוד הישן שמאזין ל-buttonClicks
    private val _buttonClicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val buttonClicks = _buttonClicks.asSharedFlow()

    /**
     * קורא מ-MainActivity.dispatchKeyEvent
     * keyCode 600/601 = לנובו
     * KEYCODE_STYLUS_BUTTON_PRIMARY/SECONDARY = סמסונג + אנדרואיד סטנדרטי
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) {
            // בולעים את ה-DOWN כדי למנוע תפריט מובנה של לנובו — אבל לא מבצעים פעולה
            return event.keyCode == 600 || event.keyCode == 601 ||
                    event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
                    event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
                    event.keyCode == KeyEvent.KEYCODE_BUTTON_1 ||
                    event.keyCode == KeyEvent.KEYCODE_BUTTON_2
        }

        return when (event.keyCode) {
            600, KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY, KeyEvent.KEYCODE_BUTTON_1 -> {
                _button1Clicks.tryEmit(Unit)
                _buttonClicks.tryEmit(Unit)
                true
            }
            601, KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY, KeyEvent.KEYCODE_BUTTON_2 -> {
                _button2Clicks.tryEmit(Unit)
                true
            }
            else -> false
        }
    }

    /**
     * קורא מ-MainActivity.onGenericMotionEvent
     * תמיכה בסמסונג S Pen ו-Lenovo Precision Pen דרך BUTTON_PRESS
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_BUTTON_PRESS) return false
        return when (event.actionButton) {
            MotionEvent.BUTTON_STYLUS_PRIMARY -> {
                _button1Clicks.tryEmit(Unit)
                _buttonClicks.tryEmit(Unit)
                true
            }
            MotionEvent.BUTTON_STYLUS_SECONDARY -> {
                _button2Clicks.tryEmit(Unit)
                true
            }
            else -> false
        }
    }
}