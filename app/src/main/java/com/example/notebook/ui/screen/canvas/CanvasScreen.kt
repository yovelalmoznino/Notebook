package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
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
import com.example.notebook.data.model.*
import com.example.notebook.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notebook.data.model.Stroke as CanvasStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(notebookId: Long, onBack: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsForTool by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addImage(it.toString()) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                            if (uiState.activeTool == CanvasTool.PEN) showSettingsForTool = CanvasTool.PEN
                            else viewModel.setActiveTool(CanvasTool.PEN)
                        }
                        ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                            if (uiState.activeTool == CanvasTool.HIGHLIGHTER) showSettingsForTool = CanvasTool.HIGHLIGHTER
                            else viewModel.setActiveTool(CanvasTool.HIGHLIGHTER)
                        }
                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) {
                            viewModel.setActiveTool(CanvasTool.ERASER)
                        }
                        ToolButton(
                            icon = when(uiState.activeShape) {
                                ShapeType.STAR -> Icons.Rounded.Star
                                ShapeType.TRIANGLE -> Icons.Rounded.ChangeHistory
                                ShapeType.RECTANGLE -> Icons.Rounded.Square
                                ShapeType.CIRCLE -> Icons.Rounded.Circle
                                ShapeType.ARROW -> Icons.Rounded.TrendingFlat
                                else -> Icons.Rounded.Category
                            },
                            isActive = uiState.activeTool == CanvasTool.SHAPE
                        ) {
                            if (uiState.activeTool == CanvasTool.SHAPE) showSettingsForTool = CanvasTool.SHAPE
                            else viewModel.setActiveTool(CanvasTool.SHAPE)
                        }

                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))
                        ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) { viewModel.setActiveTool(CanvasTool.LASSO) }
                        ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) {
                            if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*")
                            else viewModel.setActiveTool(CanvasTool.IMAGE)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                actions = {
                    if (uiState.hasLassoSelection) {
                        IconButton(onClick = { viewModel.deleteLassoSelection() }) { Icon(Icons.Rounded.Delete, null, tint = Color.Red) }
                    }
                }
            )
        }
    ) { padding ->
        showSettingsForTool?.let { tool ->
            UnifiedToolSettingsDialog(
                tool = tool,
                uiState = uiState,
                onDismiss = { showSettingsForTool = null },
                onUpdate = { color, width, penType, markerShape, shapeType ->
                    viewModel.updateToolSettings(color.toArgb(), width, tool)
                    when (tool) {
                        CanvasTool.PEN -> penType?.let { viewModel.setPenType(it) }
                        CanvasTool.HIGHLIGHTER -> markerShape?.let { viewModel.setMarkerShape(it) }
                        CanvasTool.SHAPE -> shapeType?.let { viewModel.setShapeMode(it) }
                        else -> {}
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(PastelBackground),
            userScrollEnabled = !uiState.isDrawing
        ) {
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
fun UnifiedToolSettingsDialog(
    tool: CanvasTool,
    uiState: CanvasUiState,
    onDismiss: () -> Unit,
    onUpdate: (Color, Float, PenType?, MarkerShape?, ShapeType?) -> Unit
) {
    var selectedColor by remember { mutableStateOf(Color(when(tool) {
        CanvasTool.HIGHLIGHTER -> uiState.highlighterColor
        CanvasTool.SHAPE -> uiState.shapeColor
        else -> uiState.penColor
    })) }
    var width by remember { mutableStateOf(when(tool) {
        CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth
        CanvasTool.SHAPE -> uiState.shapeWidth
        else -> uiState.penWidth
    }) } // תיקון: הוסר סוגר מיותר שהיה כאן

    var currentPenType by remember { mutableStateOf(uiState.activePenType) }
    var currentMarkerShape by remember { mutableStateOf(uiState.activeMarkerShape) }
    var currentShapeType by remember { mutableStateOf(uiState.activeShape) }

    val richPalette = listOf(
        Color(0xFF2D3436), Color(0xFF636E72), Color(0xFFB2BEC3), Color(0xFFDFE6E9), Color.White, Color.Black,
        Color(0xFFD63031), Color(0xFFE17055), Color(0xFFFDCB6E), Color(0xFFFFEAA7), Color(0xFFFAB1A0), Color(0xFFFF7675),
        Color(0xFF00B894), Color(0xFF55EFC4), Color(0xFF00CEC9), Color(0xFF81ECEC), Color(0xFF55EFC4), Color(0xFF26DE81),
        Color(0xFF0984E3), Color(0xFF74B9FF), Color(0xFF6C5CE7), Color(0xFFA29BFE), Color(0xFF45AAF2), Color(0xFF4B7BEC)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onUpdate(selectedColor, width, currentPenType, currentMarkerShape, currentShapeType); onDismiss() }) { Text("Done") } },
        title = { Text("${tool.name.lowercase().replaceFirstChar { it.uppercase() }} Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F2F6)).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val previewStroke = CanvasStroke(
                            points = if (tool == CanvasTool.SHAPE) listOf(StrokePoint(size.width * 0.3f, size.height * 0.3f, 1f), StrokePoint(size.width * 0.7f, size.height * 0.7f, 1f))
                            else listOf(StrokePoint(size.width * 0.2f, size.height / 2, 0.5f), StrokePoint(size.width * 0.5f, size.height / 2 - 20, 1f), StrokePoint(size.width * 0.8f, size.height / 2, 0.5f)),
                            color = selectedColor.toArgb(),
                            strokeWidth = width,
                            isHighlighter = tool == CanvasTool.HIGHLIGHTER,
                            penType = currentPenType,
                            markerShape = currentMarkerShape,
                            shapeType = if (tool == CanvasTool.SHAPE) currentShapeType else ShapeType.FREEHAND
                        )
                        drawComplexStroke(previewStroke, Offset.Zero)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (tool == CanvasTool.PEN) {
                    Text("Pen Style", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PenType.values().forEach { type -> FilterChip(selected = currentPenType == type, onClick = { currentPenType = type }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                    }
                } else if (tool == CanvasTool.HIGHLIGHTER) {
                    Text("Tip Shape", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarkerShape.values().forEach { shape -> FilterChip(selected = currentMarkerShape == shape, onClick = { currentMarkerShape = shape }, label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                    }
                } else if (tool == CanvasTool.SHAPE) {
                    Text("Select Shape", fontSize = 14.sp)
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(100.dp)) {
                        items(ShapeType.values().filter { it != ShapeType.FREEHAND }) { type ->
                            FilterChip(selected = currentShapeType == type, onClick = { currentShapeType = type }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.padding(2.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Thickness: ${width.toInt()}px", fontSize = 14.sp)
                Slider(value = width, onValueChange = { width = it }, valueRange = 2f..80f)

                Spacer(Modifier.height(8.dp))
                Text("Color", fontSize = 14.sp)
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(150.dp)) {
                    items(richPalette) { color ->
                        Box(modifier = Modifier.size(36.dp).padding(4.dp).background(color, CircleShape).border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape).clickable { selectedColor = color })
                    }
                }
            }
        }
    )
}

@Composable
fun PageContainer(page: PageUiModel, uiState: CanvasUiState, viewModel: CanvasViewModel) {
    Box(modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(0.7f).shadow(8.dp).background(Color.White).clipToBounds()) {
        BackgroundCanvas(page.background)
        DrawingCanvas(
            activeTool = uiState.activeTool,
            strokes = if (uiState.selectionPageId == page.page.id) page.strokes.filterNot { it.id in uiState.hiddenStrokeIds } else page.strokes,
            selectedStrokes = if (uiState.selectionPageId == page.page.id) uiState.selectedStrokes else emptyList(),
            dragOffset = Offset.Zero,
            currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null,
            lassoPath = if (uiState.drawingPageId == page.page.id || uiState.selectionPageId == page.page.id) uiState.lassoPath else emptyList(),
            onAction = { event -> viewModel.handleMotionEvent(page.page.id, event) }
        )
        page.images.forEach { img ->
            ResizableDraggableImage(img, uiState.activeTool == CanvasTool.IMAGE) { nx, ny, nw, nh ->
                viewModel.updateImageBounds(page.page.id, img.id, nx, ny, nw, nh)
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(if (isActive) Color(0xFFF1F2F6) else Color.Transparent, CircleShape).border(if (isActive) 1.dp else 0.dp, Color.LightGray, CircleShape)) {
        Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray)
    }
}

@Composable
fun ToolButtonWithMenu(icon: ImageVector, isActive: Boolean, onClick: () -> Unit, menuContent: @Composable (close: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onClick(); if (isActive) expanded = true }, modifier = Modifier.background(if (isActive) Color(0xFFF1F2F6) else Color.Transparent, CircleShape).border(if (isActive) 1.dp else 0.dp, Color.LightGray, CircleShape)) {
            Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { menuContent { expanded = false } }
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
    LaunchedEffect(img.x, img.y, img.width, img.height) { x = img.x; y = img.y; w = img.width; h = img.height }
    LaunchedEffect(img.uri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(img.uri)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = calculateInSampleSize(options, 1000, 1000); options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)?.use { val b = android.graphics.BitmapFactory.decodeStream(it, null, options); bitmap = b?.asImageBitmap() }
            } catch (_: Exception) {}
        }
    }
    Box(modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) {
        Box(modifier = Modifier.size(with(LocalDensity.current) { w.toDp() }, with(LocalDensity.current) { h.toDp() }).pointerInput(isActive) {
            if (isActive) detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount -> change.consume(); x += dragAmount.x; y += dragAmount.y }
        }) {
            bitmap?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
            if (isActive) Box(modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFF3b82f6)))
        }
        if (isActive) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(15.dp, 15.dp).size(48.dp).pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount -> change.consume(); w = (w + dragAmount.x).coerceAtLeast(100f); h = (h + dragAmount.y).coerceAtLeast(100f) }
            }) {
                Box(modifier = Modifier.size(24.dp).align(Alignment.Center).background(Color(0xFF3b82f6), CircleShape).border(2.dp, Color.White, CircleShape))
            }
        }
    }
}

fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight; val width = options.outWidth; var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2; val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) { inSampleSize *= 2 }
    }
    return inSampleSize
}

@Composable
fun BackgroundCanvas(backgroundType: PageBackground, modifier: Modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lineColor = Color.LightGray.copy(alpha = 0.4f); val spacing = 40.dp.toPx()
        when (backgroundType) {
            PageBackground.LINES -> { var curY = spacing; while (curY < size.height) { drawLine(lineColor, Offset(0f, curY), Offset(size.width, curY), 1.dp.toPx()); curY += spacing } }
            PageBackground.GRID -> {
                var curY = spacing; while (curY < size.height) { drawLine(lineColor, Offset(0f, curY), Offset(size.width, curY), 1.dp.toPx()); curY += spacing }
                var curX = spacing; while (curX < size.width) { drawLine(lineColor, Offset(curX, 0f), Offset(curX, size.height), 1.dp.toPx()); curX += spacing }
            }
            PageBackground.DOTS -> { for (x in (spacing.toInt()..size.width.toInt() step spacing.toInt())) for (y in (spacing.toInt()..size.height.toInt() step spacing.toInt())) drawCircle(lineColor, 2f, Offset(x.toFloat(), y.toFloat())) }
            else -> {}
        }
    }
}