package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.db.entity.NotebookEntity
import com.example.notebook.data.db.entity.PageEntity
import com.example.notebook.data.model.PageBackground
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

data class PageUiModel(
    val page: PageEntity,
    val strokes: List<Stroke>,
    val background: PageBackground
)

data class CanvasUiState(
    val notebookTitle: String = "",
    val pages: List<PageUiModel> = emptyList(),
    val activeTool: CanvasTool = CanvasTool.PEN,
    val penColor: Int = 0xFF000000.toInt(),
    val penWidth: Float = 4f,
    val highlighterColor: Int = 0xFFFFFF00.toInt(),
    val highlighterWidth: Float = 30f,
    val currentStroke: Stroke? = null,
    val drawingPageId: Long? = null
)

enum class CanvasTool { PEN, HIGHLIGHTER, ERASER }

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val repository: NotebookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val notebookId: Long = savedStateHandle.get<Long>("notebookId") ?: 0L
    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()
    private val gson = Gson()

    init { loadNotebookData() }

    private fun loadNotebookData() {
        viewModelScope.launch {
            repository.ensureFirstPageExists(notebookId)
            val notebook = repository.getNotebookById(notebookId)
            _uiState.update { it.copy(notebookTitle = notebook?.title ?: "") }
            refreshPages()
        }
    }

    private suspend fun refreshPages() {
        val pages = repository.getPages(notebookId)
        val listType = object : TypeToken<List<Stroke>>() {}.type
        val pageModels = pages.map { page ->
            val strokes: List<Stroke> = try {
                gson.fromJson(page.strokeDataJson, listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            PageUiModel(page, strokes, PageBackground.valueOf(page.backgroundType))
        }
        _uiState.update { it.copy(pages = pageModels) }
    }

    fun updatePageBackground(pageId: Long, background: PageBackground) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = repository.getPages(notebookId)
            pages.find { it.id == pageId }?.let { page ->
                repository.updatePage(page.copy(backgroundType = background.name))
                refreshPages()
            }
        }
    }

    fun addNewPage() {
        viewModelScope.launch {
            val pages = repository.getPages(notebookId)
            val newNum = (pages.maxOfOrNull { it.pageNumber } ?: 0) + 1
            repository.insertPage(PageEntity(notebookId = notebookId, pageNumber = newNum))
            refreshPages()
        }
    }

    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val x = event.x; val y = event.y; val pressure = event.pressure
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId) }
                if (_uiState.value.activeTool == CanvasTool.ERASER) eraseStrokesAt(pageId, x, y)
                else startNewStroke(x, y, pressure)
            }
            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (_uiState.value.activeTool == CanvasTool.ERASER) eraseStrokesAt(pageId, x, y)
                    else updateCurrentStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (_uiState.value.drawingPageId == pageId && _uiState.value.activeTool != CanvasTool.ERASER) finishStroke(pageId)
                else _uiState.update { it.copy(drawingPageId = null) }
            }
        }
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float) {
        val s = _uiState.value
        val isH = s.activeTool == CanvasTool.HIGHLIGHTER
        _uiState.update { it.copy(currentStroke = Stroke(
            points = listOf(StrokePoint(x, y, pressure)),
            color = if (isH) s.highlighterColor else s.penColor,
            strokeWidth = if (isH) s.highlighterWidth else s.penWidth,
            isHighlighter = isH
        )) }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            _uiState.update { it.copy(currentStroke = s.copy(points = s.points + StrokePoint(x, y, pressure))) }
        }
    }

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { s ->
            viewModelScope.launch(Dispatchers.IO) {
                val pageModel = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
                val updatedStrokes = pageModel.strokes + s
                repository.updatePage(pageModel.page.copy(strokeDataJson = gson.toJson(updatedStrokes)))
                refreshPages()
                _uiState.update { it.copy(currentStroke = null, drawingPageId = null) }
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val remaining = pageModel.strokes.filterNot { stroke ->
                stroke.points.any { pt -> hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) < 25.0 }
            }
            if (remaining.size != pageModel.strokes.size) {
                repository.updatePage(pageModel.page.copy(strokeDataJson = gson.toJson(remaining)))
                refreshPages()
            }
        }
    }

    fun setActiveTool(tool: CanvasTool) { _uiState.update { it.copy(activeTool = tool) } }
    fun updateToolSettings(color: Int, width: Float) {
        _uiState.update { s ->
            if (s.activeTool == CanvasTool.HIGHLIGHTER) s.copy(highlighterColor = color, highlighterWidth = width)
            else s.copy(penColor = color, penWidth = width)
        }
    }
}