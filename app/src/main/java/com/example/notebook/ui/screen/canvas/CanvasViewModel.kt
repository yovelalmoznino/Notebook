package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.db.entity.NotebookEntity
import com.example.notebook.data.model.Stroke
import com.example.notebook.data.model.StrokePoint
import com.example.notebook.data.repository.NotebookRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// הגדרת המצב של ה-UI - חייב להיות מחוץ למחלקה או מעליה
data class CanvasUiState(
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val activeTool: CanvasTool = CanvasTool.PEN
)

enum class CanvasTool {
    PEN, ERASER
}

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val repository: NotebookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // שליפת ה-ID של המחברת מהנתיב כפי שהוגדר ב-AppNavigation
    private val notebookId: Long = savedStateHandle.get<Long>("notebookId") ?: 0L

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private var currentNotebook: NotebookEntity? = null
    private val gson = Gson()

    init {
        loadNotebookData()
    }

    private fun loadNotebookData() {
        viewModelScope.launch {
            val entity = repository.getNotebookById(notebookId)
            currentNotebook = entity
            entity?.let {
                val listType = object : TypeToken<List<Stroke>>() {}.type
                val loadedStrokes: List<Stroke> = gson.fromJson(it.strokeDataJson, listType) ?: emptyList()
                _uiState.update { state -> state.copy(strokes = loadedStrokes) }
            }
        }
    }

    fun handleMotionEvent(event: MotionEvent) {
        val x = event.x
        val y = event.y
        val pressure = event.pressure

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val newStroke = Stroke(
                    points = listOf(StrokePoint(x, y, pressure)),
                    color = if (_uiState.value.activeTool == CanvasTool.PEN) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                    strokeWidth = 5f,
                    isEraser = _uiState.value.activeTool == CanvasTool.ERASER
                )
                _uiState.update { it.copy(currentStroke = newStroke) }
            }
            MotionEvent.ACTION_MOVE -> {
                _uiState.value.currentStroke?.let { stroke ->
                    val updatedPoints = stroke.points + StrokePoint(x, y, pressure)
                    _uiState.update { it.copy(currentStroke = stroke.copy(points = updatedPoints)) }
                }
            }
            MotionEvent.ACTION_UP -> {
                _uiState.value.currentStroke?.let { stroke ->
                    val updatedStrokes = _uiState.value.strokes + stroke
                    _uiState.update {
                        it.copy(
                            strokes = updatedStrokes,
                            currentStroke = null
                        )
                    }
                    saveData(updatedStrokes)
                }
            }
        }
    }

    private fun saveData(strokes: List<Stroke>) {
        viewModelScope.launch(Dispatchers.IO) {
            currentNotebook?.let { notebook ->
                val json = gson.toJson(strokes)
                // עדכון ה-Entity עם ה-JSON החדש וזמן העדכון
                repository.updateNotebook(notebook.copy(strokeDataJson = json, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun setActiveTool(tool: CanvasTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }
}