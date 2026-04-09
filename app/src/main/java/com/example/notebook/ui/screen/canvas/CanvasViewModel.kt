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
            StylusAction.UNDO              -> undo()
            StylusAction.REDO              -> redo()
            StylusAction.CYCLE_PEN_TYPE    -> cyclePenType()
            StylusAction.CYCLE_COLOR       -> cycleColor()
            StylusAction.OPEN_PALETTE      -> _uiState.update { it.copy(showColorWheel = true) }
            StylusAction.NONE              -> {}
        }
    }

    private fun cyclePenType() {
        val types   = PenType.values()
        val current = _uiState.value.activePenType
        val next    = types[(types.indexOf(current) + 1) % types.size]
        _uiState.update { it.copy(activePenType = next) }
    }

    private fun cycleColor() {
        val colors = listOf(
            0xFF2D3436.toInt(), 0xFFD63031.toInt(), 0xFF0984E3.toInt(),
            0xFF00B894.toInt(), 0xFF6C5CE7.toInt(), 0xFFE84393.toInt()
        )
        val current = _uiState.value.penColor
        val idx = colors.indexOfFirst { it == current }.let { if (it == -1) 0 else it }
        _uiState.update { it.copy(penColor = colors[(idx + 1) % colors.size]) }
    }

    fun setStylusConfig(config: StylusButtonConfig) {
        _uiState.update { it.copy(stylusConfig = config, showStylusConfigDialog = false) }
    }
    fun showStylusConfigDialog()    = _uiState.update { it.copy(showStylusConfigDialog = true) }
    fun dismissStylusConfigDialog() = _uiState.update { it.copy(showStylusConfigDialog = false) }

    // ── Undo / Redo ──────────────────────────────────────────────────
    private fun pushUndoSnapshot() {
        val snap = _uiState.value.pages
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
                pages     = prev,
                undoStack = stack.dropLast(1),
                redoStack = it.redoStack + listOf(it.pages)
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
                pages     = next,
                redoStack = stack.dropLast(1),
                undoStack = it.undoStack + listOf(it.pages)
            )
        }
        next.forEach { savePageToDb(it) }
    }

    // ── Load ──────────────────────────────────────────────────────────
    private fun loadNotebookData() {
        viewModelScope.launch {
            repository.ensureFirstPageExists(notebookId)
            val notebook = repository.getNotebookById(notebookId)
            _uiState.update { it.copy(notebookTitle = notebook?.title ?: "") }
            refreshPagesFromDb()
        }
    }

    private suspend fun refreshPagesFromDb() {
        val pages          = repository.getPages(notebookId)
        val strokeListType = object : TypeToken<List<Stroke>>() {}.type
        val imageListType  = object : TypeToken<List<CanvasImage>>() {}.type
        val pageModels = pages.map { page ->
            val strokes: List<Stroke> = try {
                gson.fromJson(page.strokeDataJson, strokeListType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            val images: List<CanvasImage> = try {
                gson.fromJson(page.imageDataJson, imageListType) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            PageUiModel(
                page,
                strokes.map { if (it.id.isEmpty()) it.copy(id = UUID.randomUUID().toString()) else it },
                images,
                runCatching { PageBackground.valueOf(page.backgroundType) }
                    .getOrDefault(PageBackground.PLAIN)
            )
        }
        _uiState.update { it.copy(pages = pageModels) }
    }

    // ── Draw Actions ──────────────────────────────────────────────────
    fun handleDrawAction(
        pageId: Long, action: Int,
        x: Float, y: Float,
        pressure: Float, isEraser: Boolean
    ) {
        val currentTool = if (isEraser) CanvasTool.ERASER else _uiState.value.activeTool

        when (action) {
            0 -> { // ACTION_DOWN
                _uiState.update { it.copy(drawingPageId = pageId, isDrawing = true) }
                when (currentTool) {
                    CanvasTool.LASSO -> {
                        val bounds = _uiState.value.selectionBounds
                        when {
                            bounds != null && hypot(x - bounds.right, y - bounds.bottom) < 55f -> {
                                isResizing = true
                                lassoDragLastPoint = Offset(x, y)
                            }
                            _uiState.value.hasLassoSelection &&
                                    bounds?.contains(Offset(x, y)) == true -> {
                                lassoDragLastPoint = Offset(x, y)
                                isResizing = false
                            }
                            else -> {
                                releaseLassoSelection()
                                _uiState.update {
                                    it.copy(lassoPath = listOf(Offset(x, y)), hasLassoSelection = false)
                                }
                            }
                        }
                    }
                    CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                    else -> startNewStroke(x, y, pressure, currentTool)
                }
            }
            2 -> { // ACTION_MOVE
                if (_uiState.value.drawingPageId == pageId) {
                    when (currentTool) {
                        CanvasTool.LASSO -> {
                            val bounds = _uiState.value.selectionBounds
                            when {
                                isResizing && lassoDragLastPoint != null && bounds != null -> {
                                    val scale = hypot(x - bounds.left, y - bounds.top) /
                                            hypot(
                                                lassoDragLastPoint!!.x - bounds.left,
                                                lassoDragLastPoint!!.y - bounds.top
                                            )
                                    if (!scale.isNaN() && scale > 0) resizeLassoSelection(scale)
                                    lassoDragLastPoint = Offset(x, y)
                                }
                                lassoDragLastPoint != null -> {
                                    moveLassoSelection(
                                        x - lassoDragLastPoint!!.x,
                                        y - lassoDragLastPoint!!.y
                                    )
                                    lassoDragLastPoint = Offset(x, y)
                                }
                                else -> _uiState.update {
                                    it.copy(lassoPath = it.lassoPath + Offset(x, y))
                                }
                            }
                        }
                        CanvasTool.ERASER -> eraseStrokesAt(pageId, x, y)
                        else -> updateCurrentStroke(x, y, pressure)
                    }
                }
            }
            1 -> { // ACTION_UP
                if (_uiState.value.drawingPageId == pageId) {
                    when (currentTool) {
                        CanvasTool.LASSO -> {
                            if (!_uiState.value.hasLassoSelection &&
                                _uiState.value.lassoPath.size > 2
                            ) finishLasso(pageId)
                            lassoDragLastPoint = null
                            isResizing = false
                        }
                        CanvasTool.ERASER -> {}
                        else -> finishStroke(pageId)
                    }
                    _uiState.update { it.copy(drawingPageId = null, isDrawing = false) }
                }
            }
        }
    }

    private fun eraseStrokesAt(pageId: Long, x: Float, y: Float) {
        val pages = _uiState.value.pages.toMutableList()
        val idx   = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val remaining = pages[idx].strokes.filterNot { s ->
                s.points.any { pt -> hypot(pt.x - x, pt.y - y) < 40f }
            }
            if (remaining.size != pages[idx].strokes.size) {
                pages[idx] = pages[idx].copy(strokes = remaining)
                _uiState.update { it.copy(pages = pages) }
                savePageToDb(pages[idx])
            }
        }
    }

    private fun finishLasso(pageId: Long) {
        val path = _uiState.value.lassoPath
        val page = _uiState.value.pages.find { it.page.id == pageId } ?: return
        val selS = page.strokes.filter { s ->
            s.points.any { isPointInPolygon(Offset(it.x, it.y), path) }
        }
        val selI = page.images.filter { img ->
            isPointInPolygon(Offset(img.x + img.width / 2, img.y + img.height / 2), path)
        }
        if (selS.isNotEmpty() || selI.isNotEmpty()) {
            val bounds = calculateBounds(selS, selI)
            _uiState.update {
                it.copy(
                    hasLassoSelection = true,
                    selectedStrokes   = selS,
                    selectedImages    = selI,
                    selectionPageId   = pageId,
                    hiddenStrokeIds   = selS.map { s -> s.id }.toSet(),
                    selectionBounds   = bounds,
                    lassoPath         = emptyList()
                )
            }
        } else _uiState.update { it.copy(lassoPath = emptyList()) }
    }

    private fun moveLassoSelection(dx: Float, dy: Float) {
        val s       = _uiState.value
        val updatedS = s.selectedStrokes.map {
            it.copy(points = it.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) })
        }
        val updatedI = s.selectedImages.map { it.copy(x = it.x + dx, y = it.y + dy) }
        _uiState.update {
            it.copy(
                selectedStrokes = updatedS,
                selectedImages  = updatedI,
                selectionBounds = calculateBounds(updatedS, updatedI)
            )
        }
    }

    private fun resizeLassoSelection(scale: Float) {
        val s      = _uiState.value
        val bounds = s.selectionBounds ?: return
        val pivot  = Offset(bounds.left, bounds.top)
        val updatedS = s.selectedStrokes.map { stroke ->
            stroke.copy(points = stroke.points.map { pt ->
                StrokePoint(
                    pivot.x + (pt.x - pivot.x) * scale,
                    pivot.y + (pt.y - pivot.y) * scale,
                    pt.pressure
                )
            })
        }
        val updatedI = s.selectedImages.map { img ->
            img.copy(
                x      = pivot.x + (img.x - pivot.x) * scale,
                y      = pivot.y + (img.y - pivot.y) * scale,
                width  = img.width  * scale,
                height = img.height * scale
            )
        }
        _uiState.update {
            it.copy(
                selectedStrokes = updatedS,
                selectedImages  = updatedI,
                selectionBounds = calculateBounds(updatedS, updatedI)
            )
        }
    }

    private fun calculateBounds(strokes: List<Stroke>, images: List<CanvasImage>): Rect? {
        if (strokes.isEmpty() && images.isEmpty()) return null
        var minX = Float.MAX_VALUE;  var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        strokes.forEach { s ->
            s.points.forEach { p ->
                minX = min(minX, p.x); minY = min(minY, p.y)
                maxX = max(maxX, p.x); maxY = max(maxY, p.y)
            }
        }
        images.forEach { i ->
            minX = min(minX, i.x);          minY = min(minY, i.y)
            maxX = max(maxX, i.x + i.width); maxY = max(maxY, i.y + i.height)
        }
        return Rect(minX - 10f, minY - 10f, maxX + 10f, maxY + 10f)
    }

    fun copyLassoSelection() {
        val s = _uiState.value
        if (s.hasLassoSelection)
            _uiState.update { it.copy(copiedStrokes = s.selectedStrokes, copiedImages = s.selectedImages) }
    }

    fun pasteSelection(pageId: Long) {
        val s   = _uiState.value
        if (s.copiedStrokes.isEmpty() && s.copiedImages.isEmpty()) return
        val pages = s.pages.toMutableList()
        val idx   = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            pushUndoSnapshot()
            val newS = s.copiedStrokes.map {
                it.copy(
                    id     = UUID.randomUUID().toString(),
                    points = it.points.map { p -> p.copy(x = p.x + 60f, y = p.y + 60f) }
                )
            }
            val newI = s.copiedImages.map {
                it.copy(id = UUID.randomUUID().toString(), x = it.x + 60f, y = it.y + 60f)
            }
            val updatedPage = pages[idx].copy(
                strokes = pages[idx].strokes + newS,
                images  = pages[idx].images  + newI
            )
            pages[idx] = updatedPage
            _uiState.update {
                it.copy(
                    pages             = pages,
                    hasLassoSelection = true,
                    selectedStrokes   = newS,
                    selectedImages    = newI,
                    selectionPageId   = pageId,
                    hiddenStrokeIds   = newS.map { st -> st.id }.toSet(),
                    selectionBounds   = calculateBounds(newS, newI),
                    lassoPath         = emptyList()
                )
            }
            savePageToDb(updatedPage)
        }
    }

    fun releaseLassoSelection() {
        val s      = _uiState.value
        val pageId = s.selectionPageId ?: run {
            _uiState.update {
                it.copy(
                    hasLassoSelection = false, lassoPath = emptyList(),
                    selectedStrokes   = emptyList(), selectedImages = emptyList(),
                    selectionPageId   = null, hiddenStrokeIds = emptySet(), selectionBounds = null
                )
            }
            return
        }
        val pages = s.pages.toMutableList()
        val idx   = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            val finalStrokes = pages[idx].strokes
                .filterNot { old -> s.selectedStrokes.any { it.id == old.id } } + s.selectedStrokes
            val finalImages  = pages[idx].images
                .filterNot { old -> s.selectedImages.any  { it.id == old.id } } + s.selectedImages
            pages[idx] = pages[idx].copy(strokes = finalStrokes, images = finalImages)
            _uiState.update {
                it.copy(
                    pages             = pages,
                    hasLassoSelection = false, lassoPath       = emptyList(),
                    selectedStrokes   = emptyList(), selectedImages = emptyList(),
                    selectionPageId   = null, hiddenStrokeIds = emptySet(), selectionBounds = null
                )
            }
            savePageToDb(pages[idx])
        }
    }

    private fun isPointInPolygon(p: Offset, poly: List<Offset>): Boolean {
        if (poly.size < 3) return false
        var res = false; var j = poly.size - 1
        for (i in poly.indices) {
            if ((poly[i].y > p.y) != (poly[j].y > p.y) &&
                p.x < (poly[j].x - poly[i].x) * (p.y - poly[i].y) /
                (poly[j].y - poly[i].y + 0.001f) + poly[i].x
            ) res = !res
            j = i
        }
        return res
    }

    private fun startNewStroke(x: Float, y: Float, pressure: Float, tool: CanvasTool) {
        val s     = _uiState.value
        val color = when (tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterColor
            CanvasTool.SHAPE       -> s.shapeColor
            else                   -> s.penColor
        }
        val width = when (tool) {
            CanvasTool.HIGHLIGHTER -> s.highlighterWidth
            CanvasTool.SHAPE       -> s.shapeWidth
            else                   -> s.penWidth
        }
        _uiState.update {
            it.copy(
                currentStroke = Stroke(
                    id           = UUID.randomUUID().toString(),
                    points       = listOf(StrokePoint(x, y, pressure)),
                    color        = color,
                    strokeWidth  = width,
                    isHighlighter = tool == CanvasTool.HIGHLIGHTER,
                    shapeType    = if (tool == CanvasTool.SHAPE) s.activeShape else ShapeType.FREEHAND,
                    penType      = s.activePenType,
                    markerShape  = s.activeMarkerShape
                )
            )
        }
    }

    private fun updateCurrentStroke(x: Float, y: Float, pressure: Float) {
        _uiState.value.currentStroke?.let { s ->
            _uiState.update {
                it.copy(currentStroke = s.copy(points = s.points + StrokePoint(x, y, pressure)))
            }
        }
    }

    private fun finishStroke(pageId: Long) {
        _uiState.value.currentStroke?.let { s ->
            val pages = _uiState.value.pages.toMutableList()
            val idx   = pages.indexOfFirst { it.page.id == pageId }
            if (idx != -1) {
                pushUndoSnapshot()
                val updated = pages[idx].copy(strokes = pages[idx].strokes + s)
                pages[idx]  = updated
                _uiState.update { it.copy(pages = pages, currentStroke = null) }
                savePageToDb(updated)
            }
        }
    }

    fun addImage(uri: String, targetPageId: Long? = null) {
        val pages  = _uiState.value.pages.toMutableList()
        val pageId = targetPageId ?: pages.firstOrNull()?.page?.id ?: return
        val idx    = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            pushUndoSnapshot()
            val newImg = CanvasImage(UUID.randomUUID().toString(), uri, 150f, 150f, 400f, 400f)
            pages[idx] = pages[idx].copy(images = pages[idx].images + newImg)
            _uiState.update { it.copy(pages = pages) }
            savePageToDb(pages[idx])
        }
    }

    fun updateImageBounds(pId: Long, iId: String, nx: Float, ny: Float, nw: Float, nh: Float) {
        val pages = _uiState.value.pages.toMutableList()
        val idx   = pages.indexOfFirst { it.page.id == pId }
        if (idx != -1) {
            pages[idx] = pages[idx].copy(
                images = pages[idx].images.map {
                    if (it.id == iId) it.copy(x = nx, y = ny, width = nw, height = nh) else it
                }
            )
            _uiState.update { it.copy(pages = pages) }
            savePageToDb(pages[idx])
        }
    }

    fun deleteLassoSelection() {
        val s      = _uiState.value
        val pageId = s.selectionPageId ?: return
        val pages  = s.pages.toMutableList()
        val idx    = pages.indexOfFirst { it.page.id == pageId }
        if (idx != -1) {
            pushUndoSnapshot()
            val updated = pages[idx].copy(
                strokes = pages[idx].strokes.filterNot { old -> s.selectedStrokes.any { it.id == old.id } },
                images  = pages[idx].images.filterNot  { old -> s.selectedImages.any  { it.id == old.id } }
            )
            pages[idx] = updated
            _uiState.update {
                it.copy(
                    pages             = pages,
                    hasLassoSelection = false, lassoPath       = emptyList(),
                    selectedStrokes   = emptyList(), selectedImages = emptyList(),
                    selectionPageId   = null, hiddenStrokeIds = emptySet(), selectionBounds = null
                )
            }
            savePageToDb(updated)
        }
    }

    fun exportToPdf(context: Context, uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pages       = _uiState.value.pages
            pages.forEachIndexed { i, model ->
                val info   = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
                val page   = pdfDocument.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = Paint().apply {
                    isAntiAlias  = true
                    style        = Paint.Style.STROKE
                    strokeJoin   = Paint.Join.ROUND
                    strokeCap    = Paint.Cap.ROUND
                }
                model.strokes.forEach { s ->
                    paint.color       = s.color
                    paint.strokeWidth = s.strokeWidth * 0.5f
                    paint.alpha       = if (s.isHighlighter) 120 else 255
                    val path = android.graphics.Path()
                    if (s.points.isNotEmpty()) {
                        path.moveTo(s.points[0].x * 0.5f, s.points[0].y * 0.5f)
                        s.points.forEach { pt -> path.lineTo(pt.x * 0.5f, pt.y * 0.5f) }
                    }
                    canvas.drawPath(path, paint)
                }
                pdfDocument.finishPage(page)
            }
            try {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { fd ->
                    FileOutputStream(fd.fileDescriptor).use { out -> pdfDocument.writeTo(out) }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally {
                pdfDocument.close()
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    // ── Setters ───────────────────────────────────────────────────────
    fun setActiveTool(tool: CanvasTool) {
        releaseLassoSelection(); _uiState.update { it.copy(activeTool = tool) }
    }
    fun setShapeMode(shape: ShapeType) {
        releaseLassoSelection(); _uiState.update { it.copy(activeShape = shape, activeTool = CanvasTool.SHAPE) }
    }
    fun setPenType(type: PenType) {
        releaseLassoSelection(); _uiState.update { it.copy(activePenType = type) }
    }
    fun setMarkerShape(shape: MarkerShape) {
        releaseLassoSelection(); _uiState.update { it.copy(activeMarkerShape = shape) }
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
    fun updatePageBackground(pageId: Long, bg: PageBackground) {
        viewModelScope.launch {
            repository.getPages(notebookId).find { it.id == pageId }?.let {
                repository.updatePage(it.copy(backgroundType = bg.name))
            }
            refreshPagesFromDb()
        }
    }
    fun addNewPage() {
        viewModelScope.launch {
            val pages  = repository.getPages(notebookId)
            val lastBg = pages.lastOrNull()?.backgroundType ?: "PLAIN"
            repository.insertPage(
                PageEntity(notebookId = notebookId, pageNumber = pages.size + 1, backgroundType = lastBg)
            )
            refreshPagesFromDb()
        }
    }
    private fun savePageToDb(pageModel: PageUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePage(
                pageModel.page.copy(
                    strokeDataJson = gson.toJson(pageModel.strokes),
                    imageDataJson  = gson.toJson(pageModel.images)
                )
            )
        }
    }
    private fun toggleToolFromButton() {
        releaseLassoSelection()
        _uiState.update {
            it.copy(
                activeTool    = if (it.activeTool == CanvasTool.ERASER) CanvasTool.PEN else CanvasTool.ERASER,
                currentStroke = null
            )
        }
    }
}