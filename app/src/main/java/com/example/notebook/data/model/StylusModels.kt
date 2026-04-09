package com.example.notebook.data.model

enum class StylusAction {
    TOGGLE_PEN_ERASER,
    UNDO,
    REDO,
    CYCLE_PEN_TYPE,
    CYCLE_COLOR,
    OPEN_PALETTE,
    NONE
}

data class StylusButtonConfig(
    val button1Action: StylusAction = StylusAction.TOGGLE_PEN_ERASER,
    val button2Action: StylusAction = StylusAction.UNDO
)