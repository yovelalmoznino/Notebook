package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.db.entity.NotebookEntity
import com.example.notebook.data.db.entity.PageEntity
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
import kotlin.math.hypot // פותר את אזהרות המתמטיקה

// הגדרת המצב של ה-UI
data class CanvasUiState(
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val activeTool: CanvasTool = CanvasTool.PEN,
    val selectedColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 5f,
    val notebookTitle: String = "",
    val currentPageIndex: Int = 0,
    val totalPages: Int = 1
)

enum class CanvasTool {
    PEN, ERASER
}

val PenColors = listOf(
    0xFF000000.toInt(), // שחור
    0xFFFF0000.toInt(), // אדום
    0xFF0000FF.toInt(), // כחול
    0xFF008000.toInt(), // ירוק
    0xFFFFA500.toInt(), // כתום
    0xFF800080.toInt()  // סגול
)

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val repository: NotebookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val notebookId: Long = savedStateHandle.get<Long>("notebookId") ?: 0L

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private var currentNotebook: NotebookEntity? = null
    private var currentPage: PageEntity? = null
    private val gson = Gson()

    init {
        loadNotebookData()
    }

    private fun loadNotebookData() {
        viewModelScope.launch {
            currentNotebook = repository.getNotebookById(notebookId)

            repository.ensureFirstPageExists(notebookId)

            val pages = repository.getPages(notebookId)

            if (pages.isNotEmpty()) {
                currentPage = pages.first()

                val listType = object : TypeToken<List<Stroke>>() {}.type
                val loadedStrokes: List<Stroke> = try {
                    gson.fromJson(currentPage?.strokeDataJson, listType) ?: emptyList()
                } catch (_: Exception) { // תוקנה אזהרת ה-e שאינו בשימוש
                    emptyList()
                }

                _uiState.update { state ->
                    state.copy(
                        strokes = loadedStrokes,
                        notebookTitle = currentNotebook?.title ?: "",
                        currentPageIndex = 0,
                        totalPages = pages.size
                    )
                }
            }
        }
    }

    fun handleMotionEvent(event: MotionEvent) {
        val toolType = event.getToolType(0)
        if (toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER) {
            return
        }

        val x = event.x
        val y = event.y
        val pressure = event.pressure

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (_uiState.value.activeTool == CanvasTool.ERASER) {
                    eraseStrokesAt(x, y)
                } else {
                    startNewStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.activeTool == CanvasTool.ERASER) {
                    eraseStrokesAt(x, y)
                } else {
                    updateCurrentStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (_uiState.value.activeTool == CanvasTool.PEN) {
                    finishStroke()
                } else {
                    saveData(_uiState.value.strokes)
                }
            }
        }
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float) {
        val newStroke = Stroke(
            points = listOf(StrokePoint(x, y, pressure)),
            color = _uiState.value.selectedColor,
            strokeWidth = _uiState.value.strokeWidth,
            isEraser = false
        )
        _uiState.update { it.copy(currentStroke = newStroke) }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { stroke ->
            val updatedPoints = stroke.points + StrokePoint(x, y, pressure)
            _uiState.update { it.copy(currentStroke = stroke.copy(points = updatedPoints)) }
        }
    }

    private fun finishStroke() {
        _uiState.value.currentStroke?.let { stroke ->
            val updatedStrokes = _uiState.value.strokes + stroke
            _uiState.update { it.copy(strokes = updatedStrokes, currentStroke = null) }
            saveData(updatedStrokes)
        }
    }

    private fun eraseStrokesAt(x: Float, y: Float) {
        _uiState.update { state ->
            val remainingStrokes = state.strokes.filterNot { stroke ->
                stroke.points.any { pt ->
                    // שימוש ב-hypot הפותר את אזהרות ה-Math
                    val distance = hypot((pt.x - x).toDouble(), (pt.y - y).toDouble())
                    distance < 25.0
                }
            }

            if (remainingStrokes.size != state.strokes.size) {
                state.copy(strokes = remainingStrokes)
            } else {
                state
            }
        }
    }

    private fun saveData(strokes: List<Stroke>) {
        viewModelScope.launch(Dispatchers.IO) {
            currentPage?.let { page ->
                val json = gson.toJson(strokes)
                val updatedPage = page.copy(strokeDataJson = json, updatedAt = System.currentTimeMillis())
                repository.updatePage(updatedPage)

                currentPage = updatedPage
            }
        }
    }

    fun setActiveTool(tool: CanvasTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun selectColor(color: Int) {
        _uiState.update { it.copy(selectedColor = color, activeTool = CanvasTool.PEN) }
    }

    fun updateWidth(width: Float) {
        _uiState.update { it.copy(strokeWidth = width) }
    }
}