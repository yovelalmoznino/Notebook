package com.example.notebook.ui.screen.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.StylusButtonManager
import com.example.notebook.data.db.entity.PageEntity
import com.example.notebook.data.model.*
import com.example.notebook.data.repository.NotebookRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot

data class CanvasUiState(
    val notebookTitle: String = "",
    val pages: List<PageUiModel> = emptyList(),
    val activeTool: CanvasTool = CanvasTool.PEN,
    val activeShape: ShapeType = ShapeType.LINE,
    val activePenType: PenType = PenType.BALLPOINT,
    val activeMarkerShape: MarkerShape = MarkerShape.ROUND,
    val penColor: Int = 0xFF2D3436.toInt(),
    val penWidth: Float = 4f,
    val highlighterColor: Int = 0x66FAB1A0.toInt(),
    val highlighterWidth: Float = 40f,
    val shapeColor: Int = 0xFF0984E3.toInt(),
    val shapeWidth: Float = 4f,
    val currentStroke: Stroke? = null,
    val drawingPageId: Long? = null,
    val lassoPath: List<Offset> = emptyList(),
    val hasLassoSelection: Boolean = false,
    val selectedStrokes: List<Stroke> = emptyList(),
    val selectedImages: List<CanvasImage> = emptyList(),
    val copiedStrokes: List<Stroke> = emptyList(),
    val copiedImages: List<CanvasImage> = emptyList(),
    val selectionPageId: Long? = null,
    val hiddenStrokeIds: Set<String> = emptySet(),
    val isDrawing: Boolean = false
)

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val repository: NotebookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val notebookId: Long = savedStateHandle.get<Long>("notebookId") ?: 0L
    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()
    private val gson = Gson()
    private var lassoDragLastPoint: Offset? = null

    init {
        loadNotebookData()
        viewModelScope.launch { StylusButtonManager.buttonClicks.collect { toggleToolFromButton() } }
    }

    private fun loadNotebookData() {
        viewModelScope.launch {
            repository.ensureFirstPageExists(notebookId)
            val notebook = repository.getNotebookById(notebookId)
            _uiState.update { it.copy(notebookTitle = notebook?.title ?: "") }
            refreshPagesFromDb()
        }
    }

    private suspend fun refreshPagesFromDb() {
        val pages = repository.getPages(notebookId)
        val strokeListType = object : TypeToken<List<Stroke>>() {}.type
        val imageListType  = object : TypeToken<List<CanvasImage>>() {}.type
        val pageModels = pages.map { page ->
            val strokes: List<Stroke> = try { gson.fromJson(page.strokeDataJson, strokeListType) ?: emptyList() } catch (_: Exception) { emptyList() }
            val images: List<CanvasImage> = try { gson.fromJson(page.imageDataJson, imageListType) ?: emptyList() } catch (_: Exception) { emptyList() }
            PageUiModel(page, strokes.map { if (it.id.isEmpty()) it.copy(id = UUID.randomUUID().toString()) else it }, images, runCatching { PageBackground.valueOf(page.backgroundType) }.getOrDefault(PageBackground.PLAIN))
        }
        _uiState.update { it.copy(pages = pageModels) }
    }

    fun exportToPdf(context: Context, uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pages = _uiState.value.pages

            // גודל דף PDF (A4)
            val pageWidth = 595
            val pageHeight = 842

            pages.forEachIndexed { index, pageModel ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)

                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }

                pageModel.strokes.forEach { stroke ->
                    paint.color = stroke.color
                    paint.strokeWidth = stroke.strokeWidth * 0.5f // התאמה לדף PDF
                    paint.alpha = if (stroke.isHighlighter) 120 else 255

                    val path = android.graphics.Path()
                    if (stroke.points.isNotEmpty()) {
                        path.moveTo(stroke.points[0].x * 0.5f, stroke.points[0].y * 0.5f)
                        stroke.points.forEach { pt -> path.lineTo(pt.x * 0.5f, pt.y * 0.5f) }
                    }
                    canvas.drawPath(path, paint)
                }
                pdfDocument.finishPage(page)
            }

            try {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { fd ->
                    FileOutputStream(fd.fileDescriptor).use { output -> pdfDocument.writeTo(output) }
                }
            } catch (e: Exception) { e.printStackTrace() } finally {
                pdfDocument.close()
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val toolType = event.getToolType(0)
        if (toolType == MotionEvent.TOOL_TYPE_FINGER && _uiState.value.activeTool != CanvasTool.LASSO) return

        val x = event.x; val y = event.y
        val pressure = if (event.pressure > 0) event.pressure else 1.0f
        val isEraserHardware = toolType == MotionEvent.TOOL_TYPE_ERASER
        val currentTool = if (isEraserHardware) CanvasTool.ERASER else _uiState.value.activeTool

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId, isDrawing = true) }
                if (currentTool == CanvasTool.ERASER) eraseStrokesAt(pageId, x, y)
                else startNewStroke(x, y, pressure, currentTool)
            }
            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (currentTool == CanvasTool.ERASER) eraseStrokesAt(pageId, x, y)
                    else updateCurrentStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (currentTool != CanvasTool.ERASER) finishStroke(pageId)
                    _uiState.update { it.copy(drawingPageId = null, isDrawing = false) }
                }
            }
        }
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val s = _uiState.value
        val color = when(tool) { CanvasTool.HIGHLIGHTER -> s.highlighterColor; CanvasTool.SHAPE -> s.shapeColor; else -> s.penColor }
        val width = when(tool) { CanvasTool.HIGHLIGHTER -> s.highlighterWidth; CanvasTool.SHAPE -> s.shapeWidth; else -> s.penWidth }
        _uiState.update { it.copy(currentStroke = Stroke(UUID.randomUUID().toString(), listOf(StrokePoint(x, y, pressure)), color, width, tool == CanvasTool.HIGHLIGHTER, if (tool == CanvasTool.SHAPE) s.activeShape else ShapeType.FREEHAND, s.activePenType, s.activeMarkerShape)) }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            val lastPoint = s.points.lastOrNull()
            if (lastPoint != null && hypot(x - lastPoint.x, y - lastPoint.y) < 1f && s.shapeType == ShapeType.FREEHAND) return
            val points = if (s.shapeType == ShapeType.FREEHAND) s.points + StrokePoint(x, y, pressure) else listOf(s.points.first(), StrokePoint(x, y, pressure))
            _uiState.update { it.copy(currentStroke = s.copy(points = points)) }
        }
    }

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { s ->
            val pages = _uiState.value.pages.toMutableList()
            val idx = pages.indexOfFirst { it.page.id == pageId }
            if (idx != -1) {
                val updated = pages[idx].copy(strokes = pages[idx].strokes + s)
                pages[idx] = updated
                _uiState.update { it.copy(pages = pages, currentStroke = null) }
                savePageToDb(updated)
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        val pages = _uiState.value.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val remaining = pages[idx].strokes.filterNot { s -> s.points.any { pt -> hypot(pt.x - x, pt.y - y) < 30f } }
            if (remaining.size != pages[idx].strokes.size) {
                val updated = pages[idx].copy(strokes = remaining)
                pages[idx] = updated; _uiState.update { it.copy(pages = pages) }; savePageToDb(updated)
            }
        }
    }

    private fun savePageToDb(pageModel: PageUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPage = pageModel.page.copy(strokeDataJson = gson.toJson(pageModel.strokes), imageDataJson = gson.toJson(pageModel.images))
            repository.updatePage(updatedPage)
        }
    }

    private fun toggleToolFromButton() { _uiState.update { it.copy(activeTool = if (it.activeTool == CanvasTool.ERASER) CanvasTool.PEN else CanvasTool.ERASER, currentStroke = null) } }
    fun setActiveTool(tool: CanvasTool) { _uiState.update { it.copy(activeTool = tool) } }
    fun setShapeMode(shape: ShapeType) { _uiState.update { it.copy(activeShape = shape, activeTool = CanvasTool.SHAPE) } }
    fun setPenType(type: PenType) { _uiState.update { it.copy(activePenType = type) } }
    fun setMarkerShape(shape: MarkerShape) { _uiState.update { it.copy(activeMarkerShape = shape) } }
    fun updateToolSettings(color: Int, width: Float, tool: CanvasTool) { _uiState.update { s -> when(tool) { CanvasTool.HIGHLIGHTER -> s.copy(highlighterColor = color, highlighterWidth = width) ; CanvasTool.SHAPE -> s.copy(shapeColor = color, shapeWidth = width) ; else -> s.copy(penColor = color, penWidth = width) } } }
    fun updatePageBackground(pageId: Long, bg: PageBackground) { viewModelScope.launch { repository.getPages(notebookId).find { it.id == pageId }?.let { repository.updatePage(it.copy(backgroundType = bg.name)) }; refreshPagesFromDb() } }
    fun addNewPage() { viewModelScope.launch { val pages = repository.getPages(notebookId); val lastBg = pages.lastOrNull()?.backgroundType ?: "PLAIN"; repository.insertPage(PageEntity(notebookId = notebookId, pageNumber = pages.size + 1, backgroundType = lastBg)); refreshPagesFromDb() } }
    fun deleteLassoSelection() {}
    fun addImage(uri: String) {}
    fun updateImageBounds(pageId: Long, imageId: String, x: Float, y: Float, w: Float, h: Float) {}
    private fun isPointInPolygon(p: Offset, poly: List<Offset>): Boolean = false
    private fun moveLassoSelection(dx: Float, dy: Float) {}
    private fun finishLasso(pageId: Long) {}
    private fun releaseLassoSelection() {}
}