package com.example.notebook.ui.screen.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.*

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
    val isDrawing: Boolean = false,
    val selectionBounds: Rect? = null,
    val undoStack: List<List<PageUiModel>> = emptyList(),
    val redoStack: List<List<PageUiModel>> = emptyList(),
    val stylusConfig: StylusButtonConfig = StylusButtonConfig(),
    val showStylusConfigDialog: Boolean = false,
    val showColorWheel: Boolean = false
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
    private var isResizing = false

    init {
        loadNotebookData()
        viewModelScope.launch {
            StylusButtonManager.button1Clicks.collect {
                executeStylusAction(_uiState.value.stylusConfig.button1Action)
            }
        }
        viewModelScope.launch {
            StylusButtonManager.button2Clicks.collect {
                executeStylusAction(_uiState.value.stylusConfig.button2Action)
            }
        }
    }

    private fun executeStylusAction(action: StylusAction) {
        when (action) {
            StylusAction.TOGGLE_PEN_ERASER -> toggleToolFromButton()
            StylusAction.UNDO -> undo()
            StylusAction.REDO -> redo()
            StylusAction.CYCLE_PEN_TYPE -> cyclePenType()
            StylusAction.CYCLE_COLOR -> cycleColor()
            StylusAction.OPEN_PALETTE -> _uiState.update { it.copy(showColorWheel = true) }
            StylusAction.NONE -> {}
        }
    }

    private fun cyclePenType() {
        val types = PenType.values()
        val current = _uiState.value.activePenType
        val next = types[(types.indexOf(current) + 1) % types.size]
        _uiState.update { it.copy(activePenType = next) }
    }

    private fun cycleColor() {
        val colors = listOf(
            0xFF2D3436.toInt(),
            0xFFD63031.toInt(),
            0xFF0984E3.toInt(),
            0xFF00B894.toInt(),
            0xFF6C5CE7.toInt(),
            0xFFE84393.toInt()
        )
        val current = _uiState.value.penColor
        val idx = colors.indexOfFirst { it == current }.let { if (it == -1) 0 else it }
        _uiState.update { it.copy(penColor = colors[(idx + 1) % colors.size]) }
    }

    fun setStylusConfig(config: StylusButtonConfig) {
        _uiState.update { it.copy(stylusConfig = config, showStylusConfigDialog = false) }
    }

    fun showStylusConfigDialog() {
        _uiState.update { it.copy(showStylusConfigDialog = true) }
    }

    fun dismissStylusConfigDialog() {
        _uiState.update { it.copy(showStylusConfigDialog = false) }
    }

    private fun pushUndoSnapshot() {
        val snap = _uiState.value.pages.map { it.copy() }
        _uiState.update {
            it.copy(
                undoStack = (it.undoStack + listOf(snap)).takeLast(30),
                redoStack = emptyList()
            )
        }
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return

        val prev = stack.last()
        _uiState.update {
            it.copy(
                pages = prev,
                undoStack = stack.dropLast(1),
                redoStack = it.redoStack + listOf(it.pages),
                currentStroke = null,
                drawingPageId = null,
                isDrawing = false
            )
        }
        prev.forEach { savePageToDb(it) }
    }

    fun redo() {
        val stack = _uiState.value.redoStack
        if (stack.isEmpty()) return

        val next = stack.last()
        _uiState.update {
            it.copy(
                pages = next,
                redoStack = stack.dropLast(1),
                undoStack = it.undoStack + listOf(it.pages),
                currentStroke = null,
                drawingPageId = null,
                isDrawing = false
            )
        }
        next.forEach { savePageToDb(it) }
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
        val imageListType = object : TypeToken<List<CanvasImage>>() {}.type

        val pageModels = pages.map { page ->
            val strokes: List<Stroke> = try {
                gson.fromJson(page.strokeDataJson, strokeListType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val images: List<CanvasImage> = try {
                gson.fromJson(page.imageDataJson, imageListType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            PageUiModel(
                page = page,
                strokes = strokes.map {
                    if (it.id.isEmpty()) it.copy(id = UUID.randomUUID().toString()) else it
                },
                images = images,
                background = runCatching {
                    PageBackground.valueOf(page.backgroundType)
                }.getOrDefault(PageBackground.PLAIN)
            )
        }

        _uiState.update { it.copy(pages = pageModels) }
    }

    fun handleDrawAction(
        pageId: Long,
        action: Int,
        x: Float,
        y: Float,
        pressure: Float,
        isEraser: Boolean
    ) {
        val currentTool = if (isEraser) CanvasTool.ERASER else _uiState.value.activeTool

        when (action) {
            0 -> {
                _uiState.update {
                    it.copy(
                        drawingPageId = pageId,
                        isDrawing = true,
                        currentStroke = null
                    )
                }

                when (currentTool) {
                    CanvasTool.LASSO -> {
                        val bounds = _uiState.value.selectionBounds
                        when {
                            bounds != null && hypot(x - bounds.right, y - bounds.bottom) < 55f -> {
                                isResizing = true
                                lassoDragLastPoint = Offset(x, y)
                            }
                            _uiState.value.hasLassoSelection && bounds?.contains(Offset(x, y)) == true -> {
                                lassoDragLastPoint = Offset(x, y)
                                isResizing = false
                            }
                            else -> {
                                releaseLassoSelection()
                                _uiState.update {
                                    it.copy(
                                        lassoPath = listOf(Offset(x, y)),
                                        hasLassoSelection = false
                                    )
                                }
                            }
                        }
                    }

                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    else -> startNewStroke(x, y, pressure, currentTool)
                }
            }

            2 -> {
                if (_uiState.value.drawingPageId != pageId) return

                when (currentTool) {
                    CanvasTool.LASSO -> {
                        val bounds = _uiState.value.selectionBounds
                        when {
                            isResizing && lassoDragLastPoint != null && bounds != null -> {
                                val startDistance = hypot(
                                    lassoDragLastPoint!!.x - bounds.left,
                                    lassoDragLastPoint!!.y - bounds.top
                                )
                                val endDistance = hypot(x - bounds.left, y - bounds.top)
                                val scale = if (startDistance > 0f) endDistance / startDistance else 1f

                                if (!scale.isNaN() && scale > 0f) {
                                    resizeLassoSelection(scale)
                                }
                                lassoDragLastPoint = Offset(x, y)
                            }

                            lassoDragLastPoint != null -> {
                                moveLassoSelection(
                                    x - lassoDragLastPoint!!.x,
                                    y - lassoDragLastPoint!!.y
                                )
                                lassoDragLastPoint = Offset(x, y)
                            }

                            else -> {
                                _uiState.update { it.copy(lassoPath = it.lassoPath + Offset(x, y)) }
                            }
                        }
                    }

                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    else -> updateCurrentStroke(x, y, pressure)
                }
            }

            1 -> {
                if (_uiState.value.drawingPageId != pageId) {
                    _uiState.update {
                        it.copy(
                            drawingPageId = null,
                            isDrawing = false,
                            currentStroke = null
                        )
                    }
                    return
                }

                when (currentTool) {
                    CanvasTool.LASSO -> {
                        if (!_uiState.value.hasLassoSelection && _uiState.value.lassoPath.size > 2) {
                            finishLasso(pageId)
                        }
                        lassoDragLastPoint = null
                        isResizing = false
                    }

                    CanvasTool.ERASER -> {}

                    else -> finishStroke(pageId)
                }

                _uiState.update {
                    it.copy(
                        drawingPageId = null,
                        isDrawing = false,
                        currentStroke = null
                    )
                }
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        val pages = _uiState.value.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            val remaining = pages[idx].strokes.filterNot { stroke ->
                stroke.points.any { point ->
                    hypot(point.x - x, point.y - y) < 40f
                }
            }

            if (remaining.size != pages[idx].strokes.size) {
                pushUndoSnapshot()
                pages[idx] = pages[idx].copy(strokes = remaining)
                _uiState.update { it.copy(pages = pages) }
                savePageToDb(pages[idx])
            }
        }
    }

    private fun finishLasso(pageId: Long) {
        val path = _uiState.value.lassoPath
        val page = _uiState.value.pages.find { it.page.id == pageId } ?: return

        val selectedStrokeList = page.strokes.filter { stroke ->
            stroke.points.any { point -> isPointInPolygon(Offset(point.x, point.y), path) }
        }

        val selectedImageList = page.images.filter { image ->
            isPointInPolygon(
                Offset(image.x + image.width / 2f, image.y + image.height / 2f),
                path
            )
        }

        if (selectedStrokeList.isNotEmpty() || selectedImageList.isNotEmpty()) {
            val bounds = calculateBounds(selectedStrokeList, selectedImageList)
            _uiState.update {
                it.copy(
                    hasLassoSelection = true,
                    selectedStrokes = selectedStrokeList,
                    selectedImages = selectedImageList,
                    selectionPageId = pageId,
                    hiddenStrokeIds = selectedStrokeList.map { stroke -> stroke.id }.toSet(),
                    selectionBounds = bounds,
                    lassoPath = emptyList()
                )
            }
        } else {
            _uiState.update { it.copy(lassoPath = emptyList()) }
        }
    }

    private fun moveLassoSelection(dx: Float, dy: Float) {
        val state = _uiState.value

        val updatedStrokes = state.selectedStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    point.copy(x = point.x + dx, y = point.y + dy)
                }
            )
        }

        val updatedImages = state.selectedImages.map { image ->
            image.copy(x = image.x + dx, y = image.y + dy)
        }

        _uiState.update {
            it.copy(
                selectedStrokes = updatedStrokes,
                selectedImages = updatedImages,
                selectionBounds = calculateBounds(updatedStrokes, updatedImages)
            )
        }
    }

    private fun resizeLassoSelection(scale: Float) {
        val state = _uiState.value
        val bounds = state.selectionBounds ?: return
        val pivot = Offset(bounds.left, bounds.top)

        val updatedStrokes = state.selectedStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { point ->
                    StrokePoint(
                        x = pivot.x + (point.x - pivot.x) * scale,
                        y = pivot.y + (point.y - pivot.y) * scale,
                        pressure = point.pressure
                    )
                }
            )
        }

        val updatedImages = state.selectedImages.map { image ->
            image.copy(
                x = pivot.x + (image.x - pivot.x) * scale,
                y = pivot.y + (image.y - pivot.y) * scale,
                width = image.width * scale,
                height = image.height * scale
            )
        }

        _uiState.update {
            it.copy(
                selectedStrokes = updatedStrokes,
                selectedImages = updatedImages,
                selectionBounds = calculateBounds(updatedStrokes, updatedImages)
            )
        }
    }

    private fun calculateBounds(strokes: List<Stroke>, images: List<CanvasImage>): Rect? {
        if (strokes.isEmpty() && images.isEmpty()) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        strokes.forEach { stroke ->
            stroke.points.forEach { point ->
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
            }
        }

        images.forEach { image ->
            minX = min(minX, image.x)
            minY = min(minY, image.y)
            maxX = max(maxX, image.x + image.width)
            maxY = max(maxY, image.y + image.height)
        }

        return Rect(minX - 10f, minY - 10f, maxX + 10f, maxY + 10f)
    }

    fun copyLassoSelection() {
        val state = _uiState.value
        if (state.hasLassoSelection) {
            _uiState.update {
                it.copy(
                    copiedStrokes = state.selectedStrokes,
                    copiedImages = state.selectedImages
                )
            }
        }
    }

    fun pasteSelection(pageId: Long) {
        val state = _uiState.value
        if (state.copiedStrokes.isEmpty() && state.copiedImages.isEmpty()) return

        val pages = state.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            pushUndoSnapshot()

            val newStrokes = state.copiedStrokes.map { stroke ->
                stroke.copy(
                    id = UUID.randomUUID().toString(),
                    points = stroke.points.map { point ->
                        point.copy(x = point.x + 60f, y = point.y + 60f)
                    }
                )
            }

            val newImages = state.copiedImages.map { image ->
                image.copy(
                    id = UUID.randomUUID().toString(),
                    x = image.x + 60f,
                    y = image.y + 60f
                )
            }

            val updatedPage = pages[idx].copy(
                strokes = pages[idx].strokes + newStrokes,
                images = pages[idx].images + newImages
            )

            pages[idx] = updatedPage

            _uiState.update {
                it.copy(
                    pages = pages,
                    hasLassoSelection = true,
                    selectedStrokes = newStrokes,
                    selectedImages = newImages,
                    selectionPageId = pageId,
                    hiddenStrokeIds = newStrokes.map { stroke -> stroke.id }.toSet(),
                    selectionBounds = calculateBounds(newStrokes, newImages),
                    lassoPath = emptyList()
                )
            }

            savePageToDb(updatedPage)
        }
    }

    fun releaseLassoSelection() {
        val state = _uiState.value
        val pageId = state.selectionPageId

        if (pageId == null) {
            _uiState.update {
                it.copy(
                    hasLassoSelection = false,
                    lassoPath = emptyList(),
                    selectedStrokes = emptyList(),
                    selectedImages = emptyList(),
                    selectionPageId = null,
                    hiddenStrokeIds = emptySet(),
                    selectionBounds = null
                )
            }
            return
        }

        val pages = state.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            val finalStrokes = pages[idx].strokes.filterNot { old ->
                state.selectedStrokes.any { it.id == old.id }
            } + state.selectedStrokes

            val finalImages = pages[idx].images.filterNot { old ->
                state.selectedImages.any { it.id == old.id }
            } + state.selectedImages

            pages[idx] = pages[idx].copy(
                strokes = finalStrokes,
                images = finalImages
            )

            _uiState.update {
                it.copy(
                    pages = pages,
                    hasLassoSelection = false,
                    lassoPath = emptyList(),
                    selectedStrokes = emptyList(),
                    selectedImages = emptyList(),
                    selectionPageId = null,
                    hiddenStrokeIds = emptySet(),
                    selectionBounds = null
                )
            }

            savePageToDb(pages[idx])
        }
    }

    private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return false

        var result = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            if ((polygon[i].y > point.y) != (polygon[j].y > point.y) &&
                point.x < (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) /
                (polygon[j].y - polygon[i].y + 0.001f) + polygon[i].x
            ) {
                result = !result
            }
            j = i
        }

        return result
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val state = _uiState.value

        val color = when (tool) {
            CanvasTool.HIGHLIGHTER -> state.highlighterColor
            CanvasTool.SHAPE -> state.shapeColor
            else -> state.penColor
        }

        val width = when (tool) {
            CanvasTool.HIGHLIGHTER -> state.highlighterWidth
            CanvasTool.SHAPE -> state.shapeWidth
            else -> state.penWidth
        }

        _uiState.update {
            it.copy(
                currentStroke = Stroke(
                    id = UUID.randomUUID().toString(),
                    points = listOf(StrokePoint(x, y, pressure)),
                    color = color,
                    strokeWidth = width,
                    isHighlighter = tool == CanvasTool.HIGHLIGHTER,
                    shapeType = if (tool == CanvasTool.SHAPE) state.activeShape else ShapeType.FREEHAND,
                    penType = state.activePenType,
                    markerShape = state.activeMarkerShape
                )
            )
        }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        val current = _uiState.value.currentStroke ?: return
        _uiState.update {
            it.copy(
                currentStroke = current.copy(
                    points = current.points + StrokePoint(x, y, pressure)
                )
            )
        }
    }

    private fun finishStroke(pageId: Long) {
        val stroke = _uiState.value.currentStroke ?: return
        val pages = _uiState.value.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1 && stroke.points.isNotEmpty()) {
            pushUndoSnapshot()
            val updatedPage = pages[idx].copy(strokes = pages[idx].strokes + stroke)
            pages[idx] = updatedPage
            _uiState.update {
                it.copy(
                    pages = pages,
                    currentStroke = null
                )
            }
            savePageToDb(updatedPage)
        }
    }

    fun addImage(uri: String, targetPageId: Long? = null) {
        val pages = _uiState.value.pages.toMutableList()
        val pageId = targetPageId ?: pages.firstOrNull()?.page?.id ?: return
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            pushUndoSnapshot()
            val newImage = CanvasImage(
                id = UUID.randomUUID().toString(),
                uri = uri,
                x = 150f,
                y = 150f,
                width = 400f,
                height = 400f
            )
            pages[idx] = pages[idx].copy(images = pages[idx].images + newImage)
            _uiState.update { it.copy(pages = pages) }
            savePageToDb(pages[idx])
        }
    }

    fun updateImageBounds(
        pageId: Long,
        imageId: String,
        newX: Float,
        newY: Float,
        newWidth: Float,
        newHeight: Float
    ) {
        val pages = _uiState.value.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            pages[idx] = pages[idx].copy(
                images = pages[idx].images.map { image ->
                    if (image.id == imageId) {
                        image.copy(
                            x = newX,
                            y = newY,
                            width = newWidth,
                            height = newHeight
                        )
                    } else {
                        image
                    }
                }
            )
            _uiState.update { it.copy(pages = pages) }
            savePageToDb(pages[idx])
        }
    }

    fun deleteLassoSelection() {
        val state = _uiState.value
        val pageId = state.selectionPageId ?: return
        val pages = state.pages.toMutableList()
        val idx = pages.indexOfFirst { it.page.id == pageId }

        if (idx != -1) {
            pushUndoSnapshot()

            val updatedPage = pages[idx].copy(
                strokes = pages[idx].strokes.filterNot { old ->
                    state.selectedStrokes.any { it.id == old.id }
                },
                images = pages[idx].images.filterNot { old ->
                    state.selectedImages.any { it.id == old.id }
                }
            )

            pages[idx] = updatedPage

            _uiState.update {
                it.copy(
                    pages = pages,
                    hasLassoSelection = false,
                    lassoPath = emptyList(),
                    selectedStrokes = emptyList(),
                    selectedImages = emptyList(),
                    selectionPageId = null,
                    hiddenStrokeIds = emptySet(),
                    selectionBounds = null
                )
            }

            savePageToDb(updatedPage)
        }
    }

    fun exportToPdf(context: Context, uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pages = _uiState.value.pages

            pages.forEachIndexed { index, model ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawColor(android.graphics.Color.WHITE)

                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }

                model.strokes.forEach { stroke ->
                    paint.color = stroke.color
                    paint.strokeWidth = stroke.strokeWidth * 0.5f
                    paint.alpha = if (stroke.isHighlighter) 120 else 255

                    val path = android.graphics.Path()
                    if (stroke.points.isNotEmpty()) {
                        path.moveTo(stroke.points[0].x * 0.5f, stroke.points[0].y * 0.5f)
                        stroke.points.forEach { point ->
                            path.lineTo(point.x * 0.5f, point.y * 0.5f)
                        }
                    }
                    canvas.drawPath(path, paint)
                }

                pdfDocument.finishPage(pdfPage)
            }

            try {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { fd ->
                    FileOutputStream(fd.fileDescriptor).use { output ->
                        pdfDocument.writeTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pdfDocument.close()
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun setActiveTool(tool: CanvasTool) {
        releaseLassoSelection()
        _uiState.update {
            it.copy(
                activeTool = tool,
                currentStroke = null,
                drawingPageId = null,
                isDrawing = false
            )
        }
    }

    fun setShapeMode(shape: ShapeType) {
        releaseLassoSelection()
        _uiState.update {
            it.copy(
                activeShape = shape,
                activeTool = CanvasTool.SHAPE,
                currentStroke = null,
                drawingPageId = null,
                isDrawing = false
            )
        }
    }

    fun setPenType(type: PenType) {
        releaseLassoSelection()
        _uiState.update { it.copy(activePenType = type) }
    }

    fun setMarkerShape(shape: MarkerShape) {
        releaseLassoSelection()
        _uiState.update { it.copy(activeMarkerShape = shape) }
    }

    fun updateToolSettings(color: Int, width: Float, tool: CanvasTool) {
        _uiState.update { state ->
            when (tool) {
                CanvasTool.HIGHLIGHTER -> state.copy(
                    highlighterColor = color,
                    highlighterWidth = width
                )
                CanvasTool.SHAPE -> state.copy(
                    shapeColor = color,
                    shapeWidth = width
                )
                else -> state.copy(
                    penColor = color,
                    penWidth = width
                )
            }
        }
    }

    fun updatePageBackground(pageId: Long, background: PageBackground) {
        viewModelScope.launch {
            repository.getPages(notebookId)
                .find { it.id == pageId }
                ?.let { page ->
                    repository.updatePage(page.copy(backgroundType = background.name))
                }

            refreshPagesFromDb()
        }
    }

    fun addNewPage() {
        viewModelScope.launch {
            val pages = repository.getPages(notebookId)
            val lastBackground = pages.lastOrNull()?.backgroundType ?: "PLAIN"

            repository.insertPage(
                PageEntity(
                    notebookId = notebookId,
                    pageNumber = pages.size + 1,
                    backgroundType = lastBackground
                )
            )
            refreshPagesFromDb()
        }
    }

    private fun savePageToDb(pageModel: PageUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(pageModel.strokes),
                    imageDataJson = gson.toJson(pageModel.images)
                )
            )
        }
    }

    private fun toggleToolFromButton() {
        releaseLassoSelection()
        _uiState.update {
            it.copy(
                activeTool = if (it.activeTool == CanvasTool.ERASER) CanvasTool.PEN else CanvasTool.ERASER,
                currentStroke = null,
                drawingPageId = null,
                isDrawing = false
            )
        }
    }
}