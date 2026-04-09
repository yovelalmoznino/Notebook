package com.example.notebook.ui.screen.canvas

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

    private fun savePageToDb(pageModel: PageUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPage = pageModel.page.copy(strokeDataJson = gson.toJson(pageModel.strokes), imageDataJson = gson.toJson(pageModel.images))
            repository.updatePage(updatedPage)
        }
    }

    private fun toggleToolFromButton() {
        _uiState.update { it.copy(activeTool = if (it.activeTool == CanvasTool.ERASER) CanvasTool.PEN else CanvasTool.ERASER, currentStroke = null) }
    }

    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val toolType = event.getToolType(0)
        // אם זו אצבע והלאסו לא פעיל - תן לה לגלול (חזרה ל-View)
        if (toolType == MotionEvent.TOOL_TYPE_FINGER && _uiState.value.activeTool != CanvasTool.LASSO) return

        val x = event.x; val y = event.y
        val pressure = if (event.pressure > 0) event.pressure else 1.0f
        val isEraserHardware = toolType == MotionEvent.TOOL_TYPE_ERASER
        val currentTool = if (isEraserHardware) CanvasTool.ERASER else _uiState.value.activeTool

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId, isDrawing = true) }
                when (currentTool) {
                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    CanvasTool.LASSO -> {
                        if (_uiState.value.hasLassoSelection && isPointInPolygon(Offset(x, y), _uiState.value.lassoPath)) {
                            lassoDragLastPoint = Offset(x, y)
                        } else {
                            releaseLassoSelection()
                            _uiState.update { it.copy(lassoPath = listOf(Offset(x, y)), hasLassoSelection = false, hiddenStrokeIds = emptySet()) }
                        }
                    }
                    else -> startNewStroke(x, y, pressure, currentTool)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.drawingPageId == pageId) {
                    when (currentTool) {
                        CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                        CanvasTool.LASSO -> {
                            if (_uiState.value.hasLassoSelection && lassoDragLastPoint != null) {
                                val dx = x - lassoDragLastPoint!!.x
                                val dy = y - lassoDragLastPoint!!.y
                                lassoDragLastPoint = Offset(x, y)
                                moveLassoSelection(dx, dy)
                            } else {
                                val lastPoint = _uiState.value.lassoPath.lastOrNull()
                                if (lastPoint == null || hypot(x - lastPoint.x, y - lastPoint.y) > 2f) {
                                    _uiState.update { it.copy(lassoPath = it.lassoPath + Offset(x, y)) }
                                }
                            }
                        }
                        else -> updateCurrentStroke(x, y, pressure)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (currentTool == CanvasTool.LASSO) {
                        if (!_uiState.value.hasLassoSelection) finishLasso(pageId)
                        lassoDragLastPoint = null
                    } else if (currentTool != CanvasTool.ERASER) finishStroke(pageId)
                    _uiState.update { it.copy(drawingPageId = null, isDrawing = false) }
                }
            }
        }
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val s = _uiState.value
        val color = when(tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterColor
            CanvasTool.SHAPE -> s.shapeColor
            else -> s.penColor
        }
        val width = when(tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterWidth
            CanvasTool.SHAPE -> s.shapeWidth
            else -> s.penWidth
        }
        _uiState.update { it.copy(currentStroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(StrokePoint(x, y, pressure)),
            color = color,
            strokeWidth = width,
            isHighlighter = tool == CanvasTool.HIGHLIGHTER,
            shapeType = if (tool == CanvasTool.SHAPE) s.activeShape else ShapeType.FREEHAND,
            penType = s.activePenType,
            markerShape = s.activeMarkerShape
        )) }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            val lastPoint = s.points.lastOrNull()
            if (lastPoint != null && hypot(x - lastPoint.x, y - lastPoint.y) < 1f && s.shapeType == ShapeType.FREEHAND) return
            val points = if (s.shapeType == ShapeType.FREEHAND) {
                s.points + StrokePoint(x, y, pressure)
            } else {
                listOf(s.points.first(), StrokePoint(x, y, pressure))
            }
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
        val s = _uiState.value; val pages = s.pages.toMutableList(); val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val remaining = pages[idx].strokes.filterNot { stroke -> stroke.points.any { pt -> hypot(pt.x - x, pt.y - y) < 30f } }
            if (s.hasLassoSelection && s.selectionPageId == pageId) {
                val remainingSelected = s.selectedStrokes.filterNot { stroke -> stroke.points.any { pt -> hypot(pt.x - x, pt.y - y) < 30f } }
                _uiState.update { it.copy(selectedStrokes = remainingSelected) }
            }
            if (remaining.size != pages[idx].strokes.size) {
                val updated = pages[idx].copy(strokes = remaining)
                pages[idx] = updated; _uiState.update { it.copy(pages = pages) }; savePageToDb(updated)
            }
        }
    }

    private fun moveLassoSelection(dx: Float, dy: Float) {
        val updatedS = _uiState.value.selectedStrokes.map { s -> s.copy(points = s.points.map { it.copy(x = it.x + dx, y = it.y + dy) }) }
        val updatedI = _uiState.value.selectedImages.map { it.copy(x = it.x + dx, y = it.y + dy) }
        val updatedPath = _uiState.value.lassoPath.map { it.copy(x = it.x + dx, y = it.y + dy) }
        _uiState.update { it.copy(selectedStrokes = updatedS, selectedImages = updatedI, lassoPath = updatedPath) }
    }

    private fun finishLasso(pageId: Long) {
        val path = _uiState.value.lassoPath
        if (path.size < 3) return
        val page = _uiState.value.pages.find { it.page.id == pageId } ?: return
        val selS = page.strokes.filter { s -> s.points.any { isPointInPolygon(Offset(it.x, it.y), path) } }
        val selI = page.images.filter { isPointInPolygon(Offset(it.x + it.width/2, it.y + it.height/2), path) }
        if (selS.isNotEmpty() || selI.isNotEmpty()) {
            _uiState.update { it.copy(hasLassoSelection = true, selectedStrokes = selS, selectedImages = selI, selectionPageId = pageId, hiddenStrokeIds = selS.map { it.id }.toSet()) }
        } else _uiState.update { it.copy(lassoPath = emptyList()) }
    }

    private fun releaseLassoSelection() {
        val s = _uiState.value; val pageId = s.selectionPageId ?: return; val pages = s.pages.toMutableList(); val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val finalStrokes = pages[idx].strokes.filterNot { old -> s.selectedStrokes.any { it.id == old.id } } + s.selectedStrokes
            val finalImages = pages[idx].images.filterNot { old -> s.selectedImages.any { it.id == old.id } } + s.selectedImages
            pages[idx] = pages[idx].copy(strokes = finalStrokes, images = finalImages)
            _uiState.update { it.copy(pages = pages, hasLassoSelection = false, lassoPath = emptyList(), selectedStrokes = emptyList(), selectedImages = emptyList(), selectionPageId = null, hiddenStrokeIds = emptySet()) }
            savePageToDb(pages[idx])
        }
    }

    fun deleteLassoSelection() {
        val s = _uiState.value; val pageId = s.selectionPageId ?: return; val pages = s.pages.toMutableList(); val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val updated = pages[idx].copy(strokes = pages[idx].strokes.filterNot { old -> s.selectedStrokes.any { it.id == old.id } }, images = pages[idx].images.filterNot { old -> s.selectedImages.any { it.id == old.id } })
            pages[idx] = updated; _uiState.update { it.copy(pages = pages, hasLassoSelection = false, lassoPath = emptyList(), selectedStrokes = emptyList(), selectedImages = emptyList(), selectionPageId = null, hiddenStrokeIds = emptySet()) }; savePageToDb(updated)
        }
    }

    private fun isPointInPolygon(p: Offset, poly: List<Offset>): Boolean {
        if (poly.size < 3) return false
        var res = false; var j = poly.size - 1
        for (i in poly.indices) {
            val dy = poly[j].y - poly[i].y
            if ((poly[i].y > p.y) != (poly[j].y > p.y)) {
                if (abs(dy) > 0.0001f) {
                    if (p.x < (poly[j].x - poly[i].x) * (p.y - poly[i].y) / dy + poly[i].x) res = !res
                }
            }
            j = i
        }
        return res
    }

    fun addImage(uri: String) {
        val pageId = _uiState.value.pages.firstOrNull()?.page?.id ?: return; val pages = _uiState.value.pages.toMutableList(); val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val newImg = CanvasImage(UUID.randomUUID().toString(), uri, 100f, 100f, 400f, 400f)
            val updated = pages[idx].copy(images = pages[idx].images + newImg); pages[idx] = updated; _uiState.update { it.copy(pages = pages) }; savePageToDb(updated)
        }
    }

    fun updateImageBounds(pageId: Long, imageId: String, x: Float, y: Float, w: Float, h: Float) {
        val pages = _uiState.value.pages.toMutableList(); val idx = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val updatedI = pages[idx].images.map { if (it.id == imageId) it.copy(x = x, y = y, width = w, height = h) else it }
            val updated = pages[idx].copy(images = updatedI); pages[idx] = updated; _uiState.update { it.copy(pages = pages) }; savePageToDb(updated)
        }
    }

    fun updatePageBackground(pageId: Long, bg: PageBackground) { viewModelScope.launch { repository.getPages(notebookId).find { it.id == pageId }?.let { repository.updatePage(it.copy(backgroundType = bg.name)) }; refreshPagesFromDb() } }
    fun addNewPage() { viewModelScope.launch { val pages = repository.getPages(notebookId); val lastBg = pages.lastOrNull()?.backgroundType ?: "PLAIN"; repository.insertPage(PageEntity(notebookId = notebookId, pageNumber = pages.size + 1, backgroundType = lastBg)); refreshPagesFromDb() } }
    fun setActiveTool(tool: CanvasTool) { _uiState.update { it.copy(activeTool = tool) } }
    fun setShapeMode(shape: ShapeType) { _uiState.update { it.copy(activeShape = shape, activeTool = CanvasTool.SHAPE) } }
    fun setPenType(type: PenType) { _uiState.update { it.copy(activePenType = type, activeTool = CanvasTool.PEN) } }
    fun setMarkerShape(shape: MarkerShape) { _uiState.update { it.copy(activeMarkerShape = shape, activeTool = CanvasTool.HIGHLIGHTER) } }
    fun updateToolSettings(color: Int, width: Float, tool: CanvasTool) { _uiState.update { s -> when(tool) { CanvasTool.HIGHLIGHTER -> s.copy(highlighterColor = color, highlighterWidth = width) ; CanvasTool.SHAPE -> s.copy(shapeColor = color, shapeWidth = width) ; else -> s.copy(penColor = color, penWidth = width) } } }
}