package com.example.notebook.ui.screen.canvas

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.CanvasImage
import com.example.notebook.data.model.PageBackground
import com.example.notebook.data.model.ShapeType
import com.example.notebook.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight; val width = options.outWidth; var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2; val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) { inSampleSize *= 2 }
    }
    return inSampleSize
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(notebookId: Long, onBack: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var toolToConfigure by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }
    var showShapeMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            viewModel.addImage(it.toString())
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) { if (uiState.activeTool == CanvasTool.PEN) toolToConfigure = CanvasTool.PEN else viewModel.setActiveTool(CanvasTool.PEN) }
                        ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) { if (uiState.activeTool == CanvasTool.HIGHLIGHTER) toolToConfigure = CanvasTool.HIGHLIGHTER else viewModel.setActiveTool(CanvasTool.HIGHLIGHTER) }
                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) { viewModel.setActiveTool(CanvasTool.ERASER) }
                        Box {
                            val shapeIcon = when(uiState.activeShape) { ShapeType.LINE -> Icons.Rounded.Remove; ShapeType.RECTANGLE -> Icons.Rounded.CheckBoxOutlineBlank; ShapeType.CIRCLE -> Icons.Rounded.RadioButtonUnchecked; else -> Icons.Rounded.Category }
                            ToolButton(shapeIcon, uiState.activeTool == CanvasTool.SHAPE) { if (uiState.activeTool == CanvasTool.SHAPE) toolToConfigure = CanvasTool.SHAPE else showShapeMenu = true }
                            DropdownMenu(expanded = showShapeMenu, onDismissRequest = { showShapeMenu = false }) {
                                DropdownMenuItem(text = { Text("Straight Line") }, onClick = { viewModel.setShapeMode(ShapeType.LINE); showShapeMenu = false }, leadingIcon = { Icon(Icons.Rounded.Remove, null) })
                                DropdownMenuItem(text = { Text("Rectangle") }, onClick = { viewModel.setShapeMode(ShapeType.RECTANGLE); showShapeMenu = false }, leadingIcon = { Icon(Icons.Rounded.CheckBoxOutlineBlank, null) })
                                DropdownMenuItem(text = { Text("Circle") }, onClick = { viewModel.setShapeMode(ShapeType.CIRCLE); showShapeMenu = false }, leadingIcon = { Icon(Icons.Rounded.RadioButtonUnchecked, null) })
                            }
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.Gray.copy(alpha = 0.3f))
                        ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) { viewModel.setActiveTool(CanvasTool.LASSO) }
                        ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) {
                            if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*") else viewModel.setActiveTool(CanvasTool.IMAGE)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (uiState.hasLassoSelection) {
                        IconButton(onClick = { viewModel.copyLassoSelection() }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = ToolbarIcon) }
                        IconButton(onClick = { viewModel.deleteLassoSelection() }) { Icon(Icons.Rounded.Delete, "Delete", tint = Color.Red) }
                        IconButton(onClick = { viewModel.clearLassoSelection() }) { Icon(Icons.Rounded.Close, "Clear", tint = ToolbarIcon) }
                    } else if (uiState.copiedStrokes.isNotEmpty() || uiState.copiedImages.isNotEmpty()) {
                        IconButton(onClick = { uiState.pages.firstOrNull()?.page?.id?.let { viewModel.pasteClipboard(it) } }) { Icon(Icons.Rounded.ContentPaste, "Paste", tint = ToolbarIcon) }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PastelSurface)
            )
        }
    ) { padding ->
        toolToConfigure?.let { tool ->
            val initialColor = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterColor; CanvasTool.SHAPE -> uiState.shapeColor; else -> uiState.penColor }
            val initialWidth = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth; CanvasTool.SHAPE -> uiState.shapeWidth; else -> uiState.penWidth }
            ColorPickerAndWidthDialog(
                title = "Tool Settings", initialColor = Color(initialColor), initialWidth = initialWidth,
                onDismiss = { toolToConfigure = null }, onSave = { c, w -> viewModel.updateToolSettings(c.toArgb(), w, tool); toolToConfigure = null }
            )
        }

        if (selectedPageForTemplate != null) {
            TemplateSelectionDialog(onDismiss = { selectedPageForTemplate = null }, onSelect = { viewModel.updatePageBackground(selectedPageForTemplate!!, it); selectedPageForTemplate = null })
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(PastelBackground), horizontalAlignment = Alignment.CenterHorizontally) {
            items(uiState.pages, key = { it.page.id }) { pageModel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { selectedPageForTemplate = pageModel.page.id }) { Icon(Icons.Rounded.Settings, "Page Settings", tint = ToolbarIcon) }
                    Box(modifier = Modifier.padding(bottom = 32.dp, start = 16.dp, end = 16.dp).fillMaxWidth().aspectRatio(0.7f).shadow(8.dp, RoundedCornerShape(4.dp)).background(Color.White).clipToBounds()) {

                        // 1. שכבת הרקע הנמוכה ביותר (משבצות/שורות)
                        BackgroundCanvas(backgroundType = pageModel.background)

                        // הכנת הנתונים למסך
                        val displayImages = if (uiState.selectionPageId == pageModel.page.id) {
                            pageModel.images.filterNot { img -> uiState.selectedImages.any { it.id == img.id } } + uiState.selectedImages
                        } else pageModel.images

                        val displayStrokes = if (uiState.selectionPageId == pageModel.page.id) {
                            pageModel.strokes.filterNot { s -> uiState.selectedStrokes.any { it.id == s.id } } + uiState.selectedStrokes
                        } else pageModel.strokes

                        // 2. שכבת התמונות האמצעית
                        displayImages.forEach { img ->
                            ResizableDraggableImage(
                                img = img,
                                isActive = uiState.activeTool == CanvasTool.IMAGE,
                                onUpdate = { nx, ny, nw, nh -> viewModel.updateImageBounds(pageModel.page.id, img.id, nx, ny, nw, nh) }
                            )
                        }

                        // 3. שכבת הציור העליונה שקולטת את העט
                        DrawingCanvas(
                            activeTool = uiState.activeTool,
                            strokes = displayStrokes,
                            currentStroke = if (uiState.drawingPageId == pageModel.page.id) uiState.currentStroke else null,
                            lassoPath = if (uiState.drawingPageId == pageModel.page.id) uiState.lassoPath else emptyList(),
                            onAction = { event -> viewModel.handleMotionEvent(pageModel.page.id, event) }
                        )
                    }
                }
            }
            item {
                Button(onClick = { viewModel.addNewPage() }, modifier = Modifier.padding(32.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Page") }
            }
        }
    }
}

// קומפוננטה חדשה וקלה לציור הרקע התחתון!
@Composable
fun BackgroundCanvas(backgroundType: PageBackground, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier) {
        val lineColor = Color.LightGray.copy(alpha = 0.5f)
        val spacing = 40.dp.toPx()
        when (backgroundType) {
            PageBackground.LINES -> { var y = spacing; while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx()); y += spacing } }
            PageBackground.GRID -> {
                var y = spacing; while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx()); y += spacing }
                var x = spacing; while (x < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx()); x += spacing }
            }
            PageBackground.DOTS -> { for (x in (spacing.toInt()..size.width.toInt() step spacing.toInt())) { for (y in (spacing.toInt()..size.height.toInt() step spacing.toInt())) { drawCircle(lineColor, radius = 2f, center = Offset(x.toFloat(), y.toFloat())) } } }
            PageBackground.PLAIN -> {}
        }
    }
}

@Composable
fun ResizableDraggableImage(img: CanvasImage, isActive: Boolean, onUpdate: (Float, Float, Float, Float) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(img.id) { mutableStateOf<ImageBitmap?>(null) }
    var x by remember { mutableStateOf(img.x) }
    var y by remember { mutableStateOf(img.y) }
    var w by remember { mutableStateOf(img.width) }
    var h by remember { mutableStateOf(img.height) }

    LaunchedEffect(img.x, img.y, img.width, img.height) { x = img.x; y = img.y; w = img.width; h = img.height }

    LaunchedEffect(img.uri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(img.uri)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = calculateInSampleSize(options, 800, 800)
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)?.use { val b = android.graphics.BitmapFactory.decodeStream(it, null, options); bitmap = b?.asImageBitmap() }
            } catch (e: Exception) {}
        }
    }

    Box(modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.size(with(LocalDensity.current) { w.toDp() }, with(LocalDensity.current) { h.toDp() })) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().pointerInput(isActive) {
                    if (isActive) { detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount -> change.consume(); x += dragAmount.x; y += dragAmount.y } }
                }
            )
        }
        if (isActive) {
            Box(modifier = Modifier.fillMaxSize().border(2.dp, ToolbarIconActive))
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(32.dp).background(ToolbarIconActive, CircleShape).border(2.dp, Color.White, CircleShape).pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount -> change.consume(); w = (w + dragAmount.x).coerceAtLeast(100f); h = (h + dragAmount.y).coerceAtLeast(100f) }
            })
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(if (isActive) PastelBackground else Color.Transparent, CircleShape)) { Icon(icon, null, tint = if (isActive) ToolbarIconActive else ToolbarIcon) }
}

@Composable
fun ColorPickerAndWidthDialog(title: String, initialColor: Color, initialWidth: Float, onDismiss: () -> Unit, onSave: (Color, Float) -> Unit) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var width by remember { mutableStateOf(initialWidth) }
    val colorPalette = remember {
        val grid = mutableListOf<Color>(); val grays = listOf(0xFFFFFFFF, 0xFFE0E0E0, 0xFFCCCCCC, 0xFFB3B3B3, 0xFF999999, 0xFF808080, 0xFF666666, 0xFF4D4D4D, 0xFF333333, 0xFF1A1A1A, 0xFF000000)
        grid.addAll(grays.map { Color(it) })
        val lightnessLevels = listOf(Pair(0.15f, 1.0f), Pair(0.35f, 1.0f), Pair(0.55f, 1.0f), Pair(1.0f, 1.0f), Pair(1.0f, 0.75f), Pair(1.0f, 0.5f))
        val hues = listOf(180f, 200f, 220f, 260f, 300f, 330f, 0f, 30f, 60f, 90f, 120f)
        for (level in lightnessLevels) { for (h in hues) { grid.add(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, level.first, level.second)))) } }; grid
    }
    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onSave(selectedColor, width) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Thickness", fontSize = 14.sp); Spacer(Modifier.weight(1f))
                    IconButton(onClick = { if (width > 1f) width -= 1f }) { Text("-", fontSize = 24.sp) }
                    Text("${String.format("%.1f", width)}px", fontSize = 14.sp)
                    IconButton(onClick = { if (width < 60f) width += 1f }) { Text("+", fontSize = 24.sp) }
                }
                Slider(value = width, onValueChange = { width = it }, valueRange = 1f..60f)
                Spacer(Modifier.height(24.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(11), modifier = Modifier.fillMaxWidth().height(220.dp).border(1.dp, Color.LightGray)) {
                    items(colorPalette) { color -> Box(modifier = Modifier.aspectRatio(1f).background(color).border(width = if (selectedColor == color) 2.dp else 0.dp, color = if (selectedColor == color) Color.Black else Color.Transparent).clickable { selectedColor = color }) }
                }
            }
        }
    )
}

@Composable
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Select Template") },
        text = { Column { PageBackground.values().forEach { type -> TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) { Text(type.name) } } } }
    )
}