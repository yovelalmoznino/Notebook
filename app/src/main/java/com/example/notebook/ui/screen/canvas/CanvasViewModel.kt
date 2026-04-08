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
import kotlin.math.hypot

// מודל עזר שמחבר בין הנתונים של הדף לציורים שעליו
data class PageUiModel(
    val page: PageEntity,
    val strokes: List<Stroke>
)

data class CanvasUiState(
    val notebookTitle: String = "",
    val pages: List<PageUiModel> = emptyList(), // עכשיו זו רשימה של דפים!
    val activeTool: CanvasTool = CanvasTool.PEN,
    val selectedColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 5f,
    val currentStroke: Stroke? = null,
    val drawingPageId: Long? = null // מזהה את הדף שעליו אנחנו מציירים כרגע
)

enum class CanvasTool { PEN, ERASER }

val PenColors = listOf(
    0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(),
    0xFF008000.toInt(), 0xFFFFA500.toInt(), 0xFF800080.toInt()
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
    private val gson = Gson()

    init {
        loadNotebookData()
    }

    private fun loadNotebookData() {
        viewModelScope.launch {
            currentNotebook = repository.getNotebookById(notebookId)
            repository.ensureFirstPageExists(notebookId)
            refreshPages()

            _uiState.update { it.copy(notebookTitle = currentNotebook?.title ?: "") }
        }
    }

    private suspend fun refreshPages() {
        val pages = repository.getPages(notebookId)
        val listType = object : TypeToken<List<Stroke>>() {}.type

        val pageModels = pages.map { page ->
            val strokes: List<Stroke> = try {
                gson.fromJson(page.strokeDataJson, listType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            PageUiModel(page, strokes)
        }

        _uiState.update { it.copy(pages = pageModels) }
    }

    // הוספת דף חדש לסוף המחברת
    fun addNewPage() {
        viewModelScope.launch {
            val pages = repository.getPages(notebookId)
            val newPageNumber = (pages.maxOfOrNull { it.pageNumber } ?: 0) + 1
            repository.insertPage(PageEntity(notebookId = notebookId, pageNumber = newPageNumber))
            refreshPages()
        }
    }

    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val x = event.x
        val y = event.y
        val pressure = event.pressure

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId) }
                if (_uiState.value.activeTool == CanvasTool.ERASER) {
                    eraseStrokesAt(pageId, x, y)
                } else {
                    startNewStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // מוודאים שאנחנו ממשיכים את הציור רק על הדף שהתחלנו בו
                if (_uiState.value.drawingPageId == pageId) {
                    if (_uiState.value.activeTool == CanvasTool.ERASER) {
                        eraseStrokesAt(pageId, x, y)
                    } else {
                        updateCurrentStroke(x, y, pressure)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (_uiState.value.activeTool == CanvasTool.PEN) {
                        finishStroke(pageId)
                    } else {
                        _uiState.update { it.copy(drawingPageId = null) }
                    }
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

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { stroke ->
            val updatedPages = _uiState.value.pages.map { pageModel ->
                if (pageModel.page.id == pageId) {
                    val updatedStrokes = pageModel.strokes + stroke
                    savePageData(pageModel.page, updatedStrokes)
                    pageModel.copy(strokes = updatedStrokes)
                } else {
                    pageModel
                }
            }

            _uiState.update {
                it.copy(
                    pages = updatedPages,
                    currentStroke = null,
                    drawingPageId = null
                )
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        val updatedPages = _uiState.value.pages.map { pageModel ->
            if (pageModel.page.id == pageId) {
                val remainingStrokes = pageModel.strokes.filterNot { stroke ->
                    stroke.points.any { pt -> hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) < 25.0 }
                }

                if (remainingStrokes.size != pageModel.strokes.size) {
                    savePageData(pageModel.page, remainingStrokes)
                    pageModel.copy(strokes = remainingStrokes)
                } else {
                    pageModel
                }
            } else {
                pageModel
            }
        }
        _uiState.update { it.copy(pages = updatedPages) }
    }

    private fun savePageData(page: PageEntity, strokes: List<Stroke>) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = gson.toJson(strokes)
            val updatedPage = page.copy(strokeDataJson = json, updatedAt = System.currentTimeMillis())
            repository.updatePage(updatedPage)
        }
    }

    fun setActiveTool(tool: CanvasTool) { _uiState.update { it.copy(activeTool = tool) } }
    fun selectColor(color: Int) { _uiState.update { it.copy(selectedColor = color, activeTool = CanvasTool.PEN) } }
    fun updateWidth(width: Float) { _uiState.update { it.copy(strokeWidth = width) } }
}