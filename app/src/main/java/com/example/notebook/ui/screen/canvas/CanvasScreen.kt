package com.example.notebook.ui.screen.canvas

import android.content.Intent
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
import com.example.notebook.data.model.*
import com.example.notebook.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(notebookId: Long, onBack: () -> Unit, viewModel: CanvasViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var toolToConfigure by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current
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
                        ToolButtonWithMenu(
                            icon = Icons.Rounded.Edit,
                            isActive = uiState.activeTool == CanvasTool.PEN,
                            onClick = { if (uiState.activeTool == CanvasTool.PEN) toolToConfigure = CanvasTool.PEN else viewModel.setActiveTool(CanvasTool.PEN) }
                        ) {
                            PenType.values().forEach { type ->
                                DropdownMenuItem(text = { Text(type.name) }, onClick = { viewModel.setPenType(type) })
                            }
                        }

                        ToolButtonWithMenu(
                            icon = Icons.Rounded.Brush,
                            isActive = uiState.activeTool == CanvasTool.HIGHLIGHTER,
                            onClick = { if (uiState.activeTool == CanvasTool.HIGHLIGHTER) toolToConfigure = CanvasTool.HIGHLIGHTER else viewModel.setActiveTool(CanvasTool.HIGHLIGHTER) }
                        ) {
                            MarkerShape.values().forEach { shape ->
                                DropdownMenuItem(text = { Text("${shape.name} Tip") }, onClick = { viewModel.setMarkerShape(shape) })
                            }
                        }

                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) { viewModel.setActiveTool(CanvasTool.ERASER) }

                        ToolButtonWithMenu(
                            icon = when(uiState.activeShape) { ShapeType.STAR -> Icons.Rounded.Star; ShapeType.TRIANGLE -> Icons.Rounded.ChangeHistory; else -> Icons.Rounded.Category },
                            isActive = uiState.activeTool == CanvasTool.SHAPE,
                            onClick = { if (uiState.activeTool == CanvasTool.SHAPE) toolToConfigure = CanvasTool.SHAPE else viewModel.setActiveTool(CanvasTool.SHAPE) }
                        ) {
                            ShapeType.values().filter { it != ShapeType.FREEHAND }.forEach { type ->
                                DropdownMenuItem(text = { Text(type.name) }, onClick = { viewModel.setShapeMode(type) })
                            }
                        }

                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))
                        ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) { viewModel.setActiveTool(CanvasTool.LASSO) }
                        ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) { if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*") else viewModel.setActiveTool(CanvasTool.IMAGE) }
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
        toolToConfigure?.let { tool ->
            ColorPickerAndWidthDialog(
                title = "Settings: ${tool.name}",
                initialColor = Color(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor),
                initialWidth = if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterWidth else if (tool == CanvasTool.SHAPE) uiState.shapeWidth else uiState.penWidth,
                onDismiss = { toolToConfigure = null },
                onSave = { c, w -> viewModel.updateToolSettings(c.toArgb(), w, tool); toolToConfigure = null }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(PastelBackground)) {
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
fun PageContainer(page: PageUiModel, uiState: CanvasUiState, viewModel: CanvasViewModel) {
    Box(modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(0.7f).shadow(8.dp).background(Color.White).clipToBounds()) {
        BackgroundCanvas(page.background)

        // הפרדת שכבות חכמה למניעת חסימת מגע
        val drawingLayer = @Composable {
            DrawingCanvas(
                activeTool = uiState.activeTool,
                strokes = if (uiState.selectionPageId == page.page.id) page.strokes.filterNot { it.id in uiState.hiddenStrokeIds } else page.strokes,
                selectedStrokes = if (uiState.selectionPageId == page.page.id) uiState.selectedStrokes else emptyList(),
                dragOffset = Offset.Zero,
                currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null,
                lassoPath = if (uiState.drawingPageId == page.page.id || uiState.selectionPageId == page.page.id) uiState.lassoPath else emptyList(),
                onAction = { event -> viewModel.handleMotionEvent(page.page.id, event) }
            )
        }

        val imagesLayer = @Composable {
            page.images.forEach { img ->
                ResizableDraggableImage(img, uiState.activeTool == CanvasTool.IMAGE) { nx, ny, nw, nh ->
                    viewModel.updateImageBounds(page.page.id, img.id, nx, ny, nw, nh)
                }
            }
        }

        if (uiState.activeTool == CanvasTool.IMAGE) {
            drawingLayer()
            imagesLayer()
        } else {
            imagesLayer()
            drawingLayer()
        }
    }
}

@Composable
fun ToolButtonWithMenu(icon: ImageVector, isActive: Boolean, onClick: () -> Unit, menuContent: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onClick(); if (isActive) expanded = true }, modifier = Modifier.background(if (isActive) Color.White else Color.Transparent, CircleShape).border(if (isActive) 1.dp else 0.dp, Color.LightGray, CircleShape)) {
            Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, content = menuContent)
    }
}

@Composable
fun ColorPickerAndWidthDialog(title: String, initialColor: Color, initialWidth: Float, onDismiss: () -> Unit, onSave: (Color, Float) -> Unit) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var width by remember { mutableStateOf(initialWidth) }

    val palette = listOf(
        listOf(Color(0xFF2D3436), Color(0xFF636E72), Color(0xFFB2BEC3), Color(0xFFDFE6E9)),
        listOf(Color(0xFFD63031), Color(0xFFE17055), Color(0xFFFDCB6E), Color(0xFFFFEAA7)),
        listOf(Color(0xFF0984E3), Color(0xFF00CEC9), Color(0xFF6C5CE7), Color(0xFFA29BFE)),
        listOf(Color(0xFF00B894), Color(0xFF55EFC4), Color(0xFFE84393), Color(0xFFFAB1A0))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(selectedColor, width) }) { Text("Done") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Thickness: ${width.toInt()}px", fontSize = 14.sp)
                Slider(value = width, onValueChange = { width = it }, valueRange = 2f..80f)
                Spacer(Modifier.height(16.dp))
                palette.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { color ->
                            Box(modifier = Modifier.size(44.dp).background(color, CircleShape).border(if (selectedColor == color) 3.dp else 0.dp, Color.Black, CircleShape).clickable { selectedColor = color })
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(if (isActive) Color.White else Color.Transparent, CircleShape).border(if (isActive) 1.dp else 0.dp, Color.LightGray, CircleShape)) {
        Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray)
    }
}

@Composable
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Template") },
        text = { Column { PageBackground.values().forEach { type -> TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) { Text(type.name) } } } }
    )
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
                options.inSampleSize = calculateInSampleSize(options, 1000, 1000)
                options.inJustDecodeBounds = false
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
                var curY = spacing; while (curY < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); curY += spacing }
                var curX = spacing; while (curX < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); curX += spacing }
            }
            PageBackground.DOTS -> { for (x in (spacing.toInt()..size.width.toInt() step spacing.toInt())) for (y in (spacing.toInt()..size.height.toInt() step spacing.toInt())) drawCircle(lineColor, 2f, Offset(x.toFloat(), y.toFloat())) }
            else -> {}
        }
    }
}