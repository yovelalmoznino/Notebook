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
    val hiddenStrokeIds: Set<String> = emptySet()
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
                if (it.id == null) it.copy(id = UUID.randomUUID().toString()) else it
            }
            val images: List<CanvasImage> = try {
                gson.fromJson(page.imageDataJson, imageListType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            PageUiModel(
                page, strokes, images,
                runCatching { PageBackground.valueOf(page.backgroundType) }
                    .getOrDefault(PageBackground.PLAIN)
            )
        }
        _uiState.update { it.copy(pages = pageModels) }
    }

    // ─────────────────────────────────────────────────────────────
    //  handleMotionEvent
    //  מבוסס על הגרסה המקורית: event.action (לא actionMasked)
    //  תוספת: זיהוי כפתור סטייל Lenovo/Wacom + לאסו
    // ─────────────────────────────────────────────────────────────
    fun handleMotionEvent(pageId: Long, event: MotionEvent) {
        val x        = event.x
        val y        = event.y
        val pressure = event.pressure

        // זיהוי כפתור סטייל
        val toolType = event.getToolType(0)
        val btnState = event.buttonState
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                toolType == MotionEvent.TOOL_TYPE_ERASER
        val isEraserButtonDown = isStylus && (
                btnState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
                        btnState and MotionEvent.BUTTON_SECONDARY      != 0 ||
                        (btnState and MotionEvent.BUTTON_PRIMARY != 0 &&
                                toolType != MotionEvent.TOOL_TYPE_FINGER)
                )
        val isHardwareEraser = toolType == MotionEvent.TOOL_TYPE_ERASER || isEraserButtonDown
        val currentTool = if (isHardwareEraser) CanvasTool.ERASER else _uiState.value.activeTool

        if (currentTool == CanvasTool.IMAGE) return

        if (currentTool != CanvasTool.LASSO && _uiState.value.hasLassoSelection) {
            releaseLassoSelection()
        }

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                _uiState.update { it.copy(drawingPageId = pageId) }
                when (currentTool) {
                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    CanvasTool.LASSO  -> {
                        if (_uiState.value.hasLassoSelection &&
                            _uiState.value.selectionPageId == pageId) {
                            if (isPointInPolygon(Offset(x, y), _uiState.value.lassoPath)) {
                                lassoDragLastPoint = Offset(x, y)
                            } else {
                                releaseLassoSelection()
                                _uiState.update {
                                    it.copy(
                                        lassoPath         = listOf(Offset(x, y)),
                                        hasLassoSelection = false,
                                        selectedStrokes   = emptyList(),
                                        selectedImages    = emptyList(),
                                        hiddenStrokeIds   = emptySet()
                                    )
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    lassoPath         = listOf(Offset(x, y)),
                                    hasLassoSelection = false,
                                    selectedStrokes   = emptyList(),
                                    selectedImages    = emptyList(),
                                    hiddenStrokeIds   = emptySet()
                                )
                            }
                        }
                    }
                    else -> startNewStroke(x, y, pressure, currentTool)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (_uiState.value.drawingPageId == pageId) {
                    when (currentTool) {
                        CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                        CanvasTool.LASSO  -> {
                            if (_uiState.value.hasLassoSelection && lassoDragLastPoint != null) {
                                val dx = x - lassoDragLastPoint!!.x
                                val dy = y - lassoDragLastPoint!!.y
                                lassoDragLastPoint = Offset(x, y)
                                _uiState.update {
                                    it.copy(
                                        selectedStrokes = it.selectedStrokes.map { s ->
                                            s.copy(points = s.points.map { pt ->
                                                pt.copy(x = pt.x + dx, y = pt.y + dy)
                                            })
                                        },
                                        selectedImages = it.selectedImages.map { img ->
                                            img.copy(x = img.x + dx, y = img.y + dy)
                                        },
                                        lassoPath = it.lassoPath.map { o ->
                                            o.copy(x = o.x + dx, y = o.y + dy)
                                        }
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(lassoPath = it.lassoPath + Offset(x, y))
                                }
                            }
                        }
                        else -> updateCurrentStroke(x, y, pressure)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (_uiState.value.drawingPageId == pageId) {
                    when (currentTool) {
                        CanvasTool.LASSO -> {
                            if (_uiState.value.hasLassoSelection && lassoDragLastPoint != null) {
                                lassoDragLastPoint = null
                                commitLassoMove(pageId)
                            } else if (!_uiState.value.hasLassoSelection) {
                                finishLasso(pageId)
                            }
                        }
                        CanvasTool.ERASER -> {
                            _uiState.update { it.copy(drawingPageId = null) }
                        }
                        else -> finishStroke(pageId)
                    }
                }
            }
        }
    }

    // ─── Stroke helpers ──────────────────────────────────────────

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val s = _uiState.value
        val strokeColor = when (tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterColor
            CanvasTool.SHAPE       -> s.shapeColor
            else                   -> s.penColor
        }
        val strokeWidth = when (tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterWidth
            CanvasTool.SHAPE       -> s.shapeWidth
            else                   -> s.penWidth
        }
        _uiState.update {
            it.copy(
                currentStroke = Stroke(
                    points        = listOf(StrokePoint(x, y, pressure)),
                    color         = strokeColor,
                    strokeWidth   = strokeWidth,
                    isHighlighter = tool == CanvasTool.HIGHLIGHTER,
                    shapeType     = if (tool == CanvasTool.SHAPE) s.activeShape else ShapeType.FREEHAND
                )
            )
        }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            val newPoints = if (s.shapeType == ShapeType.FREEHAND || s.shapeType == null)
                s.points + StrokePoint(x, y, pressure)
            else
                listOf(s.points.first(), StrokePoint(x, y, pressure))
            _uiState.update { it.copy(currentStroke = s.copy(points = newPoints)) }
        }
    }

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { s ->
            viewModelScope.launch(Dispatchers.IO) {
                val pageModel = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
                val updatedStrokes = pageModel.strokes + s
                repository.updatePage(
                    pageModel.page.copy(strokeDataJson = gson.toJson(updatedStrokes))
                )
                refreshPagesFromDb()
                _uiState.update { it.copy(currentStroke = null, drawingPageId = null) }
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val remaining = pageModel.strokes.filterNot { stroke ->
                stroke.points.any { pt ->
                    hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) < 35.0
                }
            }
            if (remaining.size != pageModel.strokes.size) {
                repository.updatePage(
                    pageModel.page.copy(strokeDataJson = gson.toJson(remaining))
                )
                refreshPagesFromDb()
            }
        }
    }

    // ─── Lasso ───────────────────────────────────────────────────

    private fun finishLasso(pageId: Long) {
        val path = _uiState.value.lassoPath
        if (path.size > 2) {
            val pageModel   = _uiState.value.pages.find { it.page.id == pageId }
            val pageStrokes = pageModel?.strokes ?: emptyList()
            val pageImages  = pageModel?.images  ?: emptyList()
            val selectedS   = pageStrokes.filter { stroke ->
                stroke.points.any { pt -> isPointInPolygon(Offset(pt.x, pt.y), path) }
            }
            val selectedI = pageImages.filter { img ->
                isPointInPolygon(Offset(img.x + img.width / 2, img.y + img.height / 2), path)
            }
            if (selectedS.isNotEmpty() || selectedI.isNotEmpty()) {
                val hiddenIds = selectedS.mapNotNull { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        hasLassoSelection = true,
                        selectedStrokes   = selectedS,
                        selectedImages    = selectedI,
                        hiddenStrokeIds   = hiddenIds,
                        selectionPageId   = pageId,
                        drawingPageId     = null
                    )
                }
                return
            }
        }
        clearLassoSelection()
    }

    private fun commitLassoMove(pageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val hiddenIds        = _uiState.value.hiddenStrokeIds
            val remainingStrokes = pageModel.strokes.filterNot { it.id in hiddenIds }
            val finalStrokes     = remainingStrokes + _uiState.value.selectedStrokes
            val finalImages      = pageModel.images.filterNot { old ->
                _uiState.value.selectedImages.any { it.id == old.id }
            } + _uiState.value.selectedImages
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(finalStrokes),
                    imageDataJson  = gson.toJson(finalImages)
                )
            )
            refreshPagesFromDb()
            _uiState.update {
                it.copy(
                    hiddenStrokeIds = it.selectedStrokes.mapNotNull { s -> s.id }.toSet(),
                    drawingPageId   = null
                )
            }
        }
    }

    private fun releaseLassoSelection() {
        val pageId = _uiState.value.selectionPageId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel    = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val hiddenIds    = _uiState.value.hiddenStrokeIds
            val finalStrokes = pageModel.strokes.filterNot { it.id in hiddenIds } +
                    _uiState.value.selectedStrokes
            val finalImages  = pageModel.images.filterNot { old ->
                _uiState.value.selectedImages.any { it.id == old.id }
            } + _uiState.value.selectedImages
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(finalStrokes),
                    imageDataJson  = gson.toJson(finalImages)
                )
            )
            refreshPagesFromDb()
            _uiState.update {
                it.copy(
                    hasLassoSelection = false,
                    lassoPath         = emptyList(),
                    selectedStrokes   = emptyList(),
                    selectedImages    = emptyList(),
                    hiddenStrokeIds   = emptySet(),
                    selectionPageId   = null
                )
            }
        }
    }

    private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        var isInside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]; val pj = polygon[j]
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            ) isInside = !isInside
            j = i
        }
        return isInside
    }

    fun copyLassoSelection() {
        _uiState.update {
            it.copy(
                copiedStrokes     = it.selectedStrokes,
                copiedImages      = it.selectedImages,
                hasLassoSelection = false,
                lassoPath         = emptyList()
            )
        }
    }

    fun clearLassoSelection() {
        if (_uiState.value.hasLassoSelection) {
            releaseLassoSelection()
        } else {
            _uiState.update {
                it.copy(
                    hasLassoSelection = false,
                    lassoPath         = emptyList(),
                    selectedStrokes   = emptyList(),
                    selectedImages    = emptyList(),
                    hiddenStrokeIds   = emptySet(),
                    selectionPageId   = null
                )
            }
        }
    }

    fun deleteLassoSelection() {
        val pageId = _uiState.value.selectionPageId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel        = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val hiddenIds        = _uiState.value.hiddenStrokeIds
            val remainingStrokes = pageModel.strokes.filterNot {
                it.id in hiddenIds || _uiState.value.selectedStrokes.any { s -> s.id == it.id }
            }
            val remainingImages = pageModel.images.filterNot { img ->
                _uiState.value.selectedImages.any { it.id == img.id }
            }
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(remainingStrokes),
                    imageDataJson  = gson.toJson(remainingImages)
                )
            )
            refreshPagesFromDb()
            _uiState.update {
                it.copy(
                    hasLassoSelection = false,
                    lassoPath         = emptyList(),
                    selectedStrokes   = emptyList(),
                    selectedImages    = emptyList(),
                    hiddenStrokeIds   = emptySet(),
                    selectionPageId   = null
                )
            }
        }
    }

    // ─── Clipboard ───────────────────────────────────────────────

    fun pasteClipboard(pageId: Long) {
        if (_uiState.value.copiedStrokes.isEmpty() &&
            _uiState.value.copiedImages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel     = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val offsetStrokes = _uiState.value.copiedStrokes.map { stroke ->
                stroke.copy(
                    id     = UUID.randomUUID().toString(),
                    points = stroke.points.map { pt -> pt.copy(x = pt.x + 50f, y = pt.y + 50f) }
                )
            }
            val offsetImages = _uiState.value.copiedImages.map { img ->
                img.copy(id = UUID.randomUUID().toString(), x = img.x + 50f, y = img.y + 50f)
            }
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(pageModel.strokes + offsetStrokes),
                    imageDataJson  = gson.toJson(pageModel.images  + offsetImages)
                )
            )
            refreshPagesFromDb()
        }
    }

    // ─── Images ──────────────────────────────────────────────────

    fun addImage(uri: String) {
        val targetPageId = _uiState.value.drawingPageId
            ?: _uiState.value.pages.firstOrNull()?.page?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel = _uiState.value.pages.find { it.page.id == targetPageId } ?: return@launch
            val newImage  = CanvasImage(UUID.randomUUID().toString(), uri, 100f, 100f, 400f, 400f)
            repository.updatePage(
                pageModel.page.copy(imageDataJson = gson.toJson(pageModel.images + newImage))
            )
            refreshPagesFromDb()
        }
    }

    fun updateImageBounds(
        pageId: Long, imageId: String,
        x: Float, y: Float, width: Float, height: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageModel     = _uiState.value.pages.find { it.page.id == pageId } ?: return@launch
            val updatedImages = pageModel.images.map {
                if (it.id == imageId) it.copy(x = x, y = y, width = width, height = height) else it
            }
            repository.updatePage(
                pageModel.page.copy(imageDataJson = gson.toJson(updatedImages))
            )
            refreshPagesFromDb()
        }
    }

    // ─── Page & Tool settings ─────────────────────────────────────

    fun updatePageBackground(pageId: Long, background: PageBackground) {
        viewModelScope.launch(Dispatchers.IO) {
            val pages = repository.getPages(notebookId)
            pages.find { it.id == pageId }?.let { page ->
                repository.updatePage(page.copy(backgroundType = background.name))
            }
            refreshPagesFromDb()
        }
    }

    fun addNewPage() {
        viewModelScope.launch(Dispatchers.IO) {
            val pages  = repository.getPages(notebookId)
            val newNum = (pages.maxOfOrNull { it.pageNumber } ?: 0) + 1
            val lastBg = pages.maxByOrNull { it.pageNumber }?.backgroundType
                ?: PageBackground.PLAIN.name
            repository.insertPage(
                PageEntity(notebookId = notebookId, pageNumber = newNum, backgroundType = lastBg)
            )
            refreshPagesFromDb()
        }
    }

    fun setActiveTool(tool: CanvasTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun setShapeMode(shape: ShapeType) {
        _uiState.update { it.copy(activeShape = shape, activeTool = CanvasTool.SHAPE) }
    }

    fun updateToolSettings(color: Int, width: Float, tool: CanvasTool) {
        _uiState.update { s ->
            when (tool) {
                CanvasTool.HIGHLIGHTER -> s.copy(highlighterColor = color, highlighterWidth = width)
                CanvasTool.SHAPE       -> s.copy(shapeColor = color, shapeWidth = width)
                else                   -> s.copy(penColor = color, penWidth = width)
            }
        }
    }
}