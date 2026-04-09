package com.example.notebook.ui.screen.canvas

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds // הייבוא שהיה חסר
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.*
import com.example.notebook.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notebook.data.model.Stroke as CanvasStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(notebookId: Long, onBack: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettingsForTool by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportToPdf(context, it) { Toast.makeText(context, "PDF Exported!", Toast.LENGTH_SHORT).show() } }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addImage(it.toString()) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp)).padding(horizontal = 12.dp, vertical = 4.dp).shadow(1.dp, RoundedCornerShape(32.dp)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) { if (uiState.activeTool == CanvasTool.PEN) showSettingsForTool = CanvasTool.PEN else { viewModel.setActiveTool(CanvasTool.PEN); showSettingsForTool = null } }
                            if (showSettingsForTool == CanvasTool.PEN) ToolSettingsPopup(CanvasTool.PEN, uiState, { showSettingsForTool = null }, viewModel)
                        }
                        Box {
                            ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) { if (uiState.activeTool == CanvasTool.HIGHLIGHTER) showSettingsForTool = CanvasTool.HIGHLIGHTER else { viewModel.setActiveTool(CanvasTool.HIGHLIGHTER); showSettingsForTool = null } }
                            if (showSettingsForTool == CanvasTool.HIGHLIGHTER) ToolSettingsPopup(CanvasTool.HIGHLIGHTER, uiState, { showSettingsForTool = null }, viewModel)
                        }
                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) { viewModel.setActiveTool(CanvasTool.ERASER); showSettingsForTool = null }
                        Box {
                            val shapeIcon = when(uiState.activeShape) { ShapeType.STAR -> Icons.Rounded.Star; ShapeType.TRIANGLE -> Icons.Rounded.ChangeHistory; ShapeType.RECTANGLE -> Icons.Rounded.Square; ShapeType.CIRCLE -> Icons.Rounded.Circle; ShapeType.ARROW -> Icons.Rounded.TrendingFlat; else -> Icons.Rounded.Category }
                            ToolButton(icon = shapeIcon, isActive = uiState.activeTool == CanvasTool.SHAPE) { if (uiState.activeTool == CanvasTool.SHAPE) showSettingsForTool = CanvasTool.SHAPE else { viewModel.setActiveTool(CanvasTool.SHAPE); showSettingsForTool = null } }
                            if (showSettingsForTool == CanvasTool.SHAPE) ToolSettingsPopup(CanvasTool.SHAPE, uiState, { showSettingsForTool = null }, viewModel)
                        }
                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))
                        ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) { viewModel.setActiveTool(CanvasTool.LASSO); showSettingsForTool = null }
                        ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) { if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*") else viewModel.setActiveTool(CanvasTool.IMAGE) }
                        IconButton(onClick = { pdfLauncher.launch("${uiState.notebookTitle}.pdf") }) { Icon(Icons.Rounded.PictureAsPdf, null, tint = Color(0xFFD63031)) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                actions = {
                    if (uiState.hasLassoSelection) {
                        IconButton(onClick = { viewModel.copyLassoSelection() }) { Icon(Icons.Rounded.ContentCopy, null) }
                        IconButton(onClick = { viewModel.deleteLassoSelection() }) { Icon(Icons.Rounded.Delete, null, tint = Color.Red) } // השורה שגרמה לשגיאה
                    }
                    if (uiState.copiedStrokes.isNotEmpty() || uiState.copiedImages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pasteSelection(uiState.pages.firstOrNull()?.page?.id ?: 0L) }) { Icon(Icons.Rounded.ContentPaste, null) }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(PastelBackground), userScrollEnabled = !uiState.isDrawing) {
            items(uiState.pages, key = { it.page.id }) { pageModel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { selectedPageForTemplate = pageModel.page.id }) { Icon(Icons.Rounded.Settings, null) }
                    PageContainer(pageModel, uiState, viewModel)
                }
            }
            item { Button(onClick = { viewModel.addNewPage() }, modifier = Modifier.padding(24.dp).fillMaxWidth()) { Text("Add New Page") } }
        }
        if (selectedPageForTemplate != null) {
            TemplateSelectionDialog(onDismiss = { selectedPageForTemplate = null }, onSelect = { viewModel.updatePageBackground(selectedPageForTemplate!!, it); selectedPageForTemplate = null })
        }
    }
}

@Composable
fun ToolSettingsPopup(tool: CanvasTool, uiState: CanvasUiState, onDismiss: () -> Unit, viewModel: CanvasViewModel) {
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, 150), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(modifier = Modifier.width(300.dp).shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F2F6)).border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    val color = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterColor; CanvasTool.SHAPE -> uiState.shapeColor; else -> uiState.penColor }
                    val width = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth; CanvasTool.SHAPE -> uiState.shapeWidth; else -> uiState.penWidth }
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = if (tool == CanvasTool.SHAPE) listOf(StrokePoint(size.width * 0.35f, size.height * 0.35f, 1f), StrokePoint(size.width * 0.65f, size.height * 0.65f, 1f))
                        else listOf(StrokePoint(size.width * 0.2f, size.height / 2 + 10, 0.5f), StrokePoint(size.width * 0.5f, size.height / 2 - 10, 1f), StrokePoint(size.width * 0.8f, size.height / 2 + 10, 0.5f))
                        drawComplexStroke(CanvasStroke(points = points, color = color, strokeWidth = width, isHighlighter = tool == CanvasTool.HIGHLIGHTER, penType = uiState.activePenType, markerShape = uiState.activeMarkerShape, shapeType = if (tool == CanvasTool.SHAPE) uiState.activeShape else ShapeType.FREEHAND), Offset.Zero)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (tool == CanvasTool.PEN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PenType.values().forEach { FilterChip(selected = uiState.activePenType == it, onClick = { viewModel.setPenType(it) }, label = { Text(it.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }) } }
                } else if (tool == CanvasTool.HIGHLIGHTER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MarkerShape.values().forEach { FilterChip(selected = uiState.activeMarkerShape == it, onClick = { viewModel.setMarkerShape(it) }, label = { Text(it.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }) } }
                } else if (tool == CanvasTool.SHAPE) {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(100.dp)) { items(ShapeType.values().filter { it != ShapeType.FREEHAND }) { FilterChip(selected = uiState.activeShape == it, onClick = { viewModel.setShapeMode(it) }, label = { Text(it.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }) } }
                }
                val currentWidth = if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterWidth else if (tool == CanvasTool.SHAPE) uiState.shapeWidth else uiState.penWidth
                Slider(value = currentWidth, onValueChange = { viewModel.updateToolSettings(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor, it, tool) }, valueRange = 2f..80f)
                val palette = listOf(Color.Black, Color.DarkGray, Color.Red, Color.Blue, Color(0xFF00B894), Color(0xFF6C5CE7), Color(0xFFE84393), Color(0xFFFDCB6E), Color.White, Color.Gray, Color.LightGray, Color(0xFFDFE6E9))
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(80.dp)) { items(palette) { c -> Box(modifier = Modifier.size(28.dp).padding(4.dp).background(c, CircleShape).border(if (Color(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor) == c) 2.dp else 0.dp, Color.Black, CircleShape).clickable { viewModel.updateToolSettings(c.toArgb(), currentWidth, tool) }) } }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(16.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp).background(if (isActive) Color(0xFFF1F2F6) else Color.Transparent, CircleShape).border(if (isActive) 1.dp else 0.dp, Color.LightGray, CircleShape)) {
        Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun PageContainer(page: PageUiModel, uiState: CanvasUiState, viewModel: CanvasViewModel) {
    Box(modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(0.7f).shadow(8.dp).background(Color.White).clipToBounds()) { // הקריאה ל-clipToBounds
        BackgroundCanvas(page.background)
        DrawingCanvas(
            activeTool = uiState.activeTool,
            strokes = if (uiState.selectionPageId == page.page.id) page.strokes.filterNot { it.id in uiState.hiddenStrokeIds } else page.strokes,
            selectedStrokes = if (uiState.selectionPageId == page.page.id) uiState.selectedStrokes else emptyList(),
            dragOffset = Offset.Zero,
            currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null,
            lassoPath = if (uiState.drawingPageId == page.page.id || uiState.selectionPageId == page.page.id) uiState.lassoPath else emptyList(),
            selectionBounds = if (uiState.selectionPageId == page.page.id) uiState.selectionBounds else null,
            onAction = { event -> viewModel.handleMotionEvent(page.page.id, event) }
        )
        page.images.forEach { img ->
            val isSelected = uiState.selectedImages.any { it.id == img.id }
            if (!isSelected) ResizableDraggableImage(img, uiState.activeTool == CanvasTool.IMAGE) { nx, ny, nw, nh -> viewModel.updateImageBounds(page.page.id, img.id, nx, ny, nw, nh) }
        }
    }
}

@Composable
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Template") }, text = { Column { PageBackground.values().forEach { type -> TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) { Text(type.name) } } } })
}

@Composable
fun ResizableDraggableImage(img: CanvasImage, isActive: Boolean, onUpdate: (Float, Float, Float, Float) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(img.id) { mutableStateOf<ImageBitmap?>(null) }
    var x by remember { mutableStateOf(img.x) }; var y by remember { mutableStateOf(img.y) }
    var w by remember { mutableStateOf(img.width) }; var h by remember { mutableStateOf(img.height) }
    LaunchedEffect(img.uri) { withContext(Dispatchers.IO) { try { val uri = android.net.Uri.parse(img.uri); context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, null).let { b -> bitmap = b?.asImageBitmap() } } } catch (_: Exception) {} } }
    Box(modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) {
        Box(modifier = Modifier.size(with(LocalDensity.current) { w.toDp() }, with(LocalDensity.current) { h.toDp() }).pointerInput(isActive) { if (isActive) detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount -> change.consume(); x += dragAmount.x; y += dragAmount.y } }) {
            bitmap?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
            if (isActive) Box(modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFF3b82f6)))
        }
    }
}

@Composable
fun BackgroundCanvas(backgroundType: PageBackground, modifier: Modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lineColor = Color.LightGray.copy(alpha = 0.3f); val spacing = 40.dp.toPx()
        if (backgroundType == PageBackground.GRID) {
            var curY = spacing; while (curY < size.height) { drawLine(lineColor, Offset(0f, curY), Offset(size.width, curY)); curY += spacing }
            var curX = spacing; while (curX < size.width) { drawLine(lineColor, Offset(curX, 0f), Offset(curX, size.height)); curX += spacing }
        }
    }
}

fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight; val width = options.outWidth; var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) { val halfHeight = height / 2; val halfWidth = width / 2; while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2 }
    return inSampleSize
}