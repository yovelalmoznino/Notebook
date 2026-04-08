package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.db.entity.PageEntity
import com.example.notebook.data.model.CanvasImage
import com.example.notebook.data.model.PageBackground
import com.example.notebook.data.model.ShapeType
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
import java.util.UUID
import javax.inject.Inject
import kotlin.math.hypot

data class PageUiModel(
    val page: PageEntity,
    val strokes: List<Stroke>,
    val images: List<CanvasImage>,
    val background: PageBackground
)

data class CanvasUiState(
    val notebookTitle: String = "",
    val pages: List<PageUiModel> = emptyList(),
    val activeTool: CanvasTool = CanvasTool.PEN,
    val activeShape: ShapeType = ShapeType.LINE,
    val penColor: Int = 0xFF000000.toInt(),
    val penWidth: Float = 4f,
    val highlighterColor: Int = 0xFFFFFF00.toInt(),
    val highlighterWidth: Float = 30f,
    val shapeColor: Int = 0xFF0000FF.toInt(),
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
    val isStylusButtonDown: Boolean = false
)

enum class CanvasTool { PEN, HIGHLIGHTER, ERASER, SHAPE, LASSO, IMAGE }

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

    init { loadNotebookData() }

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
            val parsedStrokes: List<Stroke> = try {
                gson.fromJson(page.strokeDataJson, strokeListType) ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val strokes = parsedStrokes.map {
                if (it.id.isNullOrEmpty()) it.copy(id = UUID.randomUUID().toString()) else it
            }

            val images: List<CanvasImage> = try {
                gson.fromJson(page.imageDataJson, imageListType) ?: emptyList()
            } catch (_: Exception) { emptyList() }

            PageUiModel(page, strokes, images, runCatching { PageBackground.valueOf(page.backgroundType) }.getOrDefault(PageBackground.PLAIN))
        }
        _uiState.update { it.copy(pages = pageModels) }
    }

    private fun savePageToDb(pageModel: PageUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPage = pageModel.page.copy(
                strokeDataJson = gson.toJson(pageModel.strokes),
                imageDataJson = gson.toJson(pageModel.images)
            )
            repository.updatePage(updatedPage)
        }
    }

    fun setStylusButtonState(isDown: Boolean) {
        if (_uiState.value.isStylusButtonDown != isDown) {
            _uiState.update { it.copy(isStylusButtonDown = isDown) }
            if (isDown && _uiState.value.currentStroke != null) {
                _uiState.update { it.copy(currentStroke = null) }
            }
        }
    }

    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val x = event.x; val y = event.y; val pressure = event.pressure
        val toolType = event.getToolType(0)

        val isHardwareEraser = toolType == MotionEvent.TOOL_TYPE_ERASER || _uiState.value.isStylusButtonDown
        val currentTool = if (isHardwareEraser) CanvasTool.ERASER else _uiState.value.activeTool

        if (currentTool == CanvasTool.IMAGE) return

        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            if (isHardwareEraser) eraseStrokesAt(pageId, x, y)
            return
        }

        if (currentTool != CanvasTool.LASSO && _uiState.value.hasLassoSelection) {
            releaseLassoSelection()
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId) }
                when (currentTool) {
                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    CanvasTool.LASSO  -> {
                        if (_uiState.value.hasLassoSelection && _uiState.value.selectionPageId == pageId) {
                            if (isPointInPolygon(Offset(x, y), _uiState.value.lassoPath)) {
                                lassoDragLastPoint = Offset(x, y)
                            } else {
                                releaseLassoSelection()
                                startNewLassoPath(x, y)
                            }
                        } else {
                            startNewLassoPath(x, y)
                        }
                    }
                    else -> startNewStroke(x, y, pressure, currentTool)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (currentTool == CanvasTool.ERASER) {
                        if (_uiState.value.currentStroke != null) _uiState.update { it.copy(currentStroke = null) }
                        eraseStrokesAt(pageId, x, y)
                    } else if (currentTool == CanvasTool.LASSO) {
                        if (_uiState.value.hasLassoSelection && lassoDragLastPoint != null) {
                            val dx = x - lassoDragLastPoint!!.x; val dy = y - lassoDragLastPoint!!.y
                            lassoDragLastPoint = Offset(x, y)
                            moveLassoSelection(dx, dy)
                        } else {
                            _uiState.update { it.copy(lassoPath = it.lassoPath + Offset(x, y)) }
                        }
                    } else updateCurrentStroke(x, y, pressure)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (_uiState.value.drawingPageId == pageId) {
                    if (currentTool == CanvasTool.LASSO) {
                        if (_uiState.value.hasLassoSelection) lassoDragLastPoint = null else finishLasso(pageId)
                    } else if (currentTool != CanvasTool.ERASER) {
                        finishStroke(pageId)
                    }
                    if (currentTool != CanvasTool.LASSO) _uiState.update { it.copy(drawingPageId = null) }
                }
            }
        }
    }

    private fun moveLassoSelection(dx: Float, dy: Float) {
        val updatedS = _uiState.value.selectedStrokes.map { s ->
            s.copy(points = s.points.map { pt -> pt.copy(x = pt.x + dx, y = pt.y + dy) })
        }
        val updatedI = _uiState.value.selectedImages.map { img ->
            img.copy(x = img.x + dx, y = img.y + dy)
        }
        val updatedPath = _uiState.value.lassoPath.map { it.copy(x = it.x + dx, y = it.y + dy) }
        _uiState.update { it.copy(selectedStrokes = updatedS, selectedImages = updatedI, lassoPath = updatedPath) }
    }

    private fun releaseLassoSelection() {
        val s = _uiState.value
        val pageId = s.selectionPageId ?: return
        val currentPages = s.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }

        if (pageIndex != -1) {
            val pageModel = currentPages[pageIndex]
            val selectedIds = s.selectedStrokes.map { it.id }.toSet()
            val finalStrokes = pageModel.strokes.filterNot { it.id in selectedIds } + s.selectedStrokes
            val selectedImageIds = s.selectedImages.map { it.id }.toSet()
            val finalImages = pageModel.images.filterNot { it.id in selectedImageIds } + s.selectedImages
            val updatedPageModel = pageModel.copy(strokes = finalStrokes, images = finalImages)
            currentPages[pageIndex] = updatedPageModel
            _uiState.update { it.copy(
                pages = currentPages, hasLassoSelection = false, lassoPath = emptyList(),
                selectedStrokes = emptyList(), selectedImages = emptyList(),
                selectionPageId = null, hiddenStrokeIds = emptySet()
            ) }
            savePageToDb(updatedPageModel)
        }
    }

    private fun finishLasso(pageId: Long) {
        val path = _uiState.value.lassoPath
        if (path.size > 2) {
            val pageModel = _uiState.value.pages.find { it.page.id == pageId }
            val selectedS = pageModel?.strokes?.filter { stroke ->
                stroke.points.any { pt -> isPointInPolygon(Offset(pt.x, pt.y), path) }
            } ?: emptyList()
            val selectedI = pageModel?.images?.filter { img ->
                isPointInPolygon(Offset(img.x + img.width / 2, img.y + img.height / 2), path)
            } ?: emptyList()

            if (selectedS.isNotEmpty() || selectedI.isNotEmpty()) {
                _uiState.update { it.copy(
                    hasLassoSelection = true, selectedStrokes = selectedS,
                    selectedImages = selectedI, selectionPageId = pageId,
                    hiddenStrokeIds = selectedS.map { it.id }.toSet()
                ) }
                return
            }
        }
        _uiState.update { it.copy(lassoPath = emptyList()) }
    }

    private fun startNewLassoPath(x: Float, y: Float) {
        _uiState.update { it.copy(
            lassoPath = listOf(Offset(x, y)), hasLassoSelection = false,
            selectedStrokes = emptyList(), selectedImages = emptyList(), hiddenStrokeIds = emptySet()
        ) }
    }

    private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        var isInside = false; var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]; val pj = polygon[j]
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x) isInside = !isInside
            j = i
        }
        return isInside
    }

    fun copyLassoSelection() {
        val s = _uiState.value
        _uiState.update { it.copy(
            copiedStrokes = s.selectedStrokes.map { it.copy(id = UUID.randomUUID().toString()) },
            copiedImages = s.selectedImages.map { it.copy(id = UUID.randomUUID().toString()) }
        ) }
        clearLassoSelection()
    }

    fun clearLassoSelection() {
        if (_uiState.value.hasLassoSelection) releaseLassoSelection() else {
            _uiState.update { it.copy(lassoPath = emptyList(), hasLassoSelection = false, hiddenStrokeIds = emptySet()) }
        }
    }

    fun deleteLassoSelection() {
        val s = _uiState.value
        val pageId = s.selectionPageId ?: return
        val currentPages = s.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }

        if (pageIndex != -1) {
            val pageModel = currentPages[pageIndex]
            val selIds = s.selectedStrokes.map { it.id }.toSet()
            val selImgIds = s.selectedImages.map { it.id }.toSet()

            val updatedPageModel = pageModel.copy(
                strokes = pageModel.strokes.filterNot { it.id in selIds },
                images = pageModel.images.filterNot { it.id in selImgIds }
            )
            currentPages[pageIndex] = updatedPageModel

            // תיקון קריטי: איפוס מלא של מצב הלאסו כדי שהציורים יעלמו מהמסך מיד
            _uiState.update { it.copy(
                pages = currentPages,
                hasLassoSelection = false,
                selectedStrokes = emptyList(),
                selectedImages = emptyList(),
                selectionPageId = null,
                hiddenStrokeIds = emptySet(),
                lassoPath = emptyList()
            ) }
            savePageToDb(updatedPageModel)
        }
    }

    fun pasteClipboard(pageId: Long) {
        val s = _uiState.value
        if (s.copiedStrokes.isEmpty() && s.copiedImages.isEmpty()) return
        val currentPages = s.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }
        if (pageIndex != -1) {
            val pageModel = currentPages[pageIndex]
            val offsetS = s.copiedStrokes.map { it.copy(id = UUID.randomUUID().toString(), points = it.points.map { pt -> pt.copy(x = pt.x + 50f, y = pt.y + 50f) }) }
            val offsetI = s.copiedImages.map { it.copy(id = UUID.randomUUID().toString(), x = it.x + 50f, y = it.y + 50f) }
            val updated = pageModel.copy(strokes = pageModel.strokes + offsetS, images = pageModel.images + offsetI)
            currentPages[pageIndex] = updated; _uiState.update { it.copy(pages = currentPages) }; savePageToDb(updated)
        }
    }

    fun addImage(uri: String) {
        val pageId = _uiState.value.pages.firstOrNull()?.page?.id ?: return
        val currentPages = _uiState.value.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }
        if (pageIndex != -1) {
            val newImg = CanvasImage(UUID.randomUUID().toString(), uri, 100f, 100f, 400f, 400f)
            val updated = currentPages[pageIndex].copy(images = currentPages[pageIndex].images + newImg)
            currentPages[pageIndex] = updated; _uiState.update { it.copy(pages = currentPages) }; savePageToDb(updated)
        }
    }

    fun updateImageBounds(pageId: Long, imageId: String, x: Float, y: Float, width: Float, height: Float) {
        val currentPages = _uiState.value.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }
        if (pageIndex != -1) {
            val updatedI = currentPages[pageIndex].images.map { if (it.id == imageId) it.copy(x = x, y = y, width = width, height = height) else it }
            val updated = currentPages[pageIndex].copy(images = updatedI)
            currentPages[pageIndex] = updated; _uiState.update { it.copy(pages = currentPages) }; savePageToDb(updated)
        }
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val s = _uiState.value; val isH = tool == CanvasTool.HIGHLIGHTER
        val color = when(tool) { CanvasTool.HIGHLIGHTER -> s.highlighterColor; CanvasTool.SHAPE -> s.shapeColor; else -> s.penColor }
        val width = when(tool) { CanvasTool.HIGHLIGHTER -> s.highlighterWidth; CanvasTool.SHAPE -> s.shapeWidth; else -> s.penWidth }
        _uiState.update { it.copy(currentStroke = Stroke(id = UUID.randomUUID().toString(), points = listOf(StrokePoint(x, y, pressure)), color = color, strokeWidth = width, isHighlighter = isH, shapeType = if (tool == CanvasTool.SHAPE) s.activeShape else ShapeType.FREEHAND)) }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            val points = if (s.shapeType == ShapeType.FREEHAND || s.shapeType == null) s.points + StrokePoint(x, y, pressure) else listOf(s.points.first(), StrokePoint(x, y, pressure))
            _uiState.update { it.copy(currentStroke = s.copy(points = points)) }
        }
    }

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { s ->
            val currentPages = _uiState.value.pages.toMutableList()
            val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }
            if (pageIndex != -1) {
                val updated = currentPages[pageIndex].copy(strokes = currentPages[pageIndex].strokes + s)
                currentPages[pageIndex] = updated; _uiState.update { it.copy(pages = currentPages, currentStroke = null) }; savePageToDb(updated)
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        val currentPages = _uiState.value.pages.toMutableList()
        val pageIndex = currentPages.indexOfFirst { it.page.id == pageId }
        if (pageIndex != -1) {
            val remaining = currentPages[pageIndex].strokes.filterNot { s -> s.points.any { pt -> hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) < 35.0 } }
            if (remaining.size != currentPages[pageIndex].strokes.size) {
                val updated = currentPages[pageIndex].copy(strokes = remaining)
                currentPages[pageIndex] = updated; _uiState.update { it.copy(pages = currentPages) }; savePageToDb(updated)
            }
        }
    }

    fun updatePageBackground(pageId: Long, background: PageBackground) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getPages(notebookId).find { it.id == pageId }?.let { page ->
                repository.updatePage(page.copy(backgroundType = background.name))
                refreshPagesFromDb()
            }
        }
    }

    fun addNewPage() {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = repository.getPages(notebookId)
            val lastBg = pages.maxByOrNull { it.pageNumber }?.backgroundType ?: PageBackground.PLAIN.name
            repository.insertPage(PageEntity(notebookId = notebookId, pageNumber = (pages.maxOfOrNull { it.pageNumber } ?: 0) + 1, backgroundType = lastBg))
            refreshPagesFromDb()
        }
    }

    fun setActiveTool(tool: CanvasTool) { _uiState.update { it.copy(activeTool = tool) } }
    fun setShapeMode(shape: ShapeType) { _uiState.update { it.copy(activeShape = shape, activeTool = CanvasTool.SHAPE) } }
    fun updateToolSettings(color: Int, width: Float, tool: CanvasTool) {
        _uiState.update { s ->
            when(tool) {
                CanvasTool.HIGHLIGHTER -> s.copy(highlighterColor = color, highlighterWidth = width)
                CanvasTool.SHAPE -> s.copy(shapeColor = color, shapeWidth = width)
                else -> s.copy(penColor = color, penWidth = width)
            }
        }
    }
}