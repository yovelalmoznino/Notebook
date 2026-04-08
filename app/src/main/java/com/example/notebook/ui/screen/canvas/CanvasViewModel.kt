package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.model.Stroke
import com.example.notebook.data.model.StrokePoint
import com.example.notebook.data.repository.NotebookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CanvasUiState(
    val notebookTitle: String = "Notebook",
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val activeTool: DrawingTool = DrawingTool.PEN, // שינינו מ-isEraser ל-activeTool
    val isLoading: Boolean = true
)

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val notebookRepo: NotebookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState = _uiState.asStateFlow()

    fun loadNotebook(id: Long) {
        viewModelScope.launch {
            notebookRepo.getNotebookById(id)?.let { nb ->
                _uiState.update { it.copy(notebookTitle = nb.title, isLoading = false) }
            }
        }
    }

    fun setTool(tool: DrawingTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun handleMotionEvent(event: MotionEvent) {
        // Palm Rejection: רק עט (Stylus) יכול לצייר
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val newPoint = StrokePoint(event.x, event.y, event.pressure)
                _uiState.update { it.copy(
                    currentStroke = Stroke(
                        points = listOf(newPoint),
                        color = if (it.activeTool == DrawingTool.ERASER) 0 else -0x1000000,
                        strokeWidth = 5f * event.pressure,
                        isEraser = it.activeTool == DrawingTool.ERASER
                    )
                ) }
            }
            MotionEvent.ACTION_MOVE -> {
                _uiState.value.currentStroke?.let { current ->
                    val newPoint = StrokePoint(event.x, event.y, event.pressure)
                    _uiState.update { it.copy(
                        currentStroke = current.copy(points = current.points + newPoint)
                    ) }
                }
            }
            MotionEvent.ACTION_UP -> {
                _uiState.value.currentStroke?.let { finalStroke ->
                    _uiState.update { it.copy(
                        strokes = it.strokes + finalStroke,
                        currentStroke = null
                    ) }
                }
            }
        }
    }
}