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
    var showSettingsForTool by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.addImage(it.toString()) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .shadow(2.dp, RoundedCornerShape(32.dp)),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // כפתור עט
                        Box {
                            ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                                if (uiState.activeTool == CanvasTool.PEN) showSettingsForTool = CanvasTool.PEN
                                else { viewModel.setActiveTool(CanvasTool.PEN); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.PEN) {
                                ToolSettingsPopup(CanvasTool.PEN, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }

                        // כפתור מרקר
                        Box {
                            ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                                if (uiState.activeTool == CanvasTool.HIGHLIGHTER) showSettingsForTool = CanvasTool.HIGHLIGHTER
                                else { viewModel.setActiveTool(CanvasTool.HIGHLIGHTER); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.HIGHLIGHTER) {
                                ToolSettingsPopup(CanvasTool.HIGHLIGHTER, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }

                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) {
                            viewModel.setActiveTool(CanvasTool.ERASER); showSettingsForTool = null
                        }

                        // כפתור צורות
                        Box {
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
                                else { viewModel.setActiveTool(CanvasTool.SHAPE); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.SHAPE) {
                                ToolSettingsPopup(CanvasTool.SHAPE, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }

                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

                        ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) {
                            viewModel.setActiveTool(CanvasTool.LASSO); showSettingsForTool = null
                        }

                        ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) {
                            if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*")
                            else { viewModel.setActiveTool(CanvasTool.IMAGE); showSettingsForTool = null }
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
fun ToolSettingsPopup(
    tool: CanvasTool,
    uiState: CanvasUiState,
    onDismiss: () -> Unit,
    viewModel: CanvasViewModel
) {
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 160), // מיקום מתחת לבר העליון
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 1. תצוגה מקדימה קטנה
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8F9FA))
                        .border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewColor = when(tool) {
                        CanvasTool.HIGHLIGHTER -> uiState.highlighterColor
                        CanvasTool.SHAPE -> uiState.shapeColor
                        else -> uiState.penColor
                    }
                    val previewWidth = when(tool) {
                        CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth
                        CanvasTool.SHAPE -> uiState.shapeWidth
                        else -> uiState.penWidth
                    }

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val previewStroke = CanvasStroke(
                            points = if (tool == CanvasTool.SHAPE)
                                listOf(StrokePoint(size.width * 0.35f, size.height * 0.35f, 1f), StrokePoint(size.width * 0.65f, size.height * 0.65f, 1f))
                            else
                                listOf(StrokePoint(size.width * 0.2f, size.height / 2, 0.5f), StrokePoint(size.width * 0.5f, size.height / 2 - 10, 1f), StrokePoint(size.width * 0.8f, size.height / 2, 0.5f)),
                            color = previewColor,
                            strokeWidth = previewWidth,
                            isHighlighter = tool == CanvasTool.HIGHLIGHTER,
                            penType = uiState.activePenType,
                            markerShape = uiState.activeMarkerShape,
                            shapeType = if (tool == CanvasTool.SHAPE) uiState.activeShape else ShapeType.FREEHAND
                        )
                        drawComplexStroke(previewStroke, Offset.Zero)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2. הגדרות ספציפיות לכלי
                when (tool) {
                    CanvasTool.PEN -> {
                        Text("Style", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            PenType.values().forEach { type ->
                                FilterChip(
                                    selected = uiState.activePenType == type,
                                    onClick = { viewModel.setPenType(type) },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                    CanvasTool.HIGHLIGHTER -> {
                        Text("Tip", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            MarkerShape.values().forEach { shape ->
                                FilterChip(
                                    selected = uiState.activeMarkerShape == shape,
                                    onClick = { viewModel.setMarkerShape(shape) },
                                    label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                    CanvasTool.SHAPE -> {
                        Text("Shapes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(100.dp).padding(vertical = 8.dp)) {
                            items(ShapeType.values().filter { it != ShapeType.FREEHAND }) { type ->
                                FilterChip(
                                    selected = uiState.activeShape == type,
                                    onClick = { viewModel.setShapeMode(type) },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                // 3. עובי
                val currentWidth = if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterWidth else if (tool == CanvasTool.SHAPE) uiState.shapeWidth else uiState.penWidth
                Text("Size: ${currentWidth.toInt()}px", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Slider(
                    value = currentWidth,
                    onValueChange = { viewModel.updateToolSettings(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor, it, tool) },
                    valueRange = 2f..80f
                )

                // 4. צבעים
                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                val colors = listOf(
                    Color.Black, Color.DarkGray, Color(0xFFD63031), Color(0xFFE17055),
                    Color(0xFF0984E3), Color(0xFF00CEC9), Color(0xFF6C5CE7), Color(0xFF00B894)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    colors.forEach { color ->
                        val isSelected = Color(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor) == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(color, CircleShape)
                                .border(if (isSelected) 3.dp else 0.dp, Color.LightGray, CircleShape)
                                .clickable { viewModel.updateToolSettings(color.toArgb(), currentWidth, tool) }
                        )
                    }
                }

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(if (isActive) Color(0xFFF1F2F6) else Color.Transparent, CircleShape)
            .border(if (isActive) 1.5.dp else 0.dp, Color(0xFFDEE2E6), CircleShape)
    ) {
        Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray, modifier = Modifier.size(24.dp))
    }
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
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Page Template") }, text = { Column { PageBackground.values().forEach { type -> TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) { Text(type.name) } } } })
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