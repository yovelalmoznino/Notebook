package com.example.notebook.ui.screen.canvas

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.*
import com.example.notebook.ui.theme.*
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp)).padding(horizontal = 12.dp, vertical = 4.dp).shadow(1.dp, RoundedCornerShape(32.dp)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // עט
                        Box {
                            ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                                if (uiState.activeTool == CanvasTool.PEN) showSettingsForTool = CanvasTool.PEN else { viewModel.setActiveTool(CanvasTool.PEN); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.PEN) ToolSettingsPopup(CanvasTool.PEN, uiState, { showSettingsForTool = null }, viewModel)
                        }
                        // מרקר
                        Box {
                            ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                                if (uiState.activeTool == CanvasTool.HIGHLIGHTER) showSettingsForTool = CanvasTool.HIGHLIGHTER else { viewModel.setActiveTool(CanvasTool.HIGHLIGHTER); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.HIGHLIGHTER) ToolSettingsPopup(CanvasTool.HIGHLIGHTER, uiState, { showSettingsForTool = null }, viewModel)
                        }
                        // מחק
                        ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) { viewModel.setActiveTool(CanvasTool.ERASER); showSettingsForTool = null }
                        // צורות
                        Box {
                            ToolButton(Icons.Rounded.Category, uiState.activeTool == CanvasTool.SHAPE) {
                                if (uiState.activeTool == CanvasTool.SHAPE) showSettingsForTool = CanvasTool.SHAPE else { viewModel.setActiveTool(CanvasTool.SHAPE); showSettingsForTool = null }
                            }
                            if (showSettingsForTool == CanvasTool.SHAPE) ToolSettingsPopup(CanvasTool.SHAPE, uiState, { showSettingsForTool = null }, viewModel)
                        }

                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

                        IconButton(onClick = { pdfLauncher.launch("${uiState.notebookTitle}.pdf") }) {
                            Icon(Icons.Rounded.PictureAsPdf, null, tint = Color(0xFFD63031))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } }
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
    }
}

@Composable
fun ToolSettingsPopup(tool: CanvasTool, uiState: CanvasUiState, onDismiss: () -> Unit, viewModel: CanvasViewModel) {
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, 150), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(modifier = Modifier.width(280.dp).shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp)) {
                // תצוגה מקדימה
                Box(modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8F9FA)).border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    val color = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterColor; CanvasTool.SHAPE -> uiState.shapeColor; else -> uiState.penColor }
                    val width = when(tool) { CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth; CanvasTool.SHAPE -> uiState.shapeWidth; else -> uiState.penWidth }
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawComplexStroke(CanvasStroke(points = listOf(StrokePoint(size.width * 0.2f, size.height / 2, 0.5f), StrokePoint(size.width * 0.8f, size.height / 2, 0.5f)), color = color, strokeWidth = width, isHighlighter = tool == CanvasTool.HIGHLIGHTER, penType = uiState.activePenType, markerShape = uiState.activeMarkerShape, shapeType = if (tool == CanvasTool.SHAPE) uiState.activeShape else ShapeType.FREEHAND), Offset.Zero)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (tool == CanvasTool.PEN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { PenType.values().forEach { FilterChip(selected = uiState.activePenType == it, onClick = { viewModel.setPenType(it) }, label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, fontSize = 11.sp) }) } }
                } else if (tool == CanvasTool.SHAPE) {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(80.dp)) { items(ShapeType.values().filter { it != ShapeType.FREEHAND }) { FilterChip(selected = uiState.activeShape == it, onClick = { viewModel.setShapeMode(it) }, label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, fontSize = 10.sp) }) } }
                }
                val currentWidth = if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterWidth else if (tool == CanvasTool.SHAPE) uiState.shapeWidth else uiState.penWidth
                Slider(value = currentWidth, onValueChange = { viewModel.updateToolSettings(if (tool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else if (tool == CanvasTool.SHAPE) uiState.shapeColor else uiState.penColor, it, tool) }, valueRange = 2f..80f)
                val colors = listOf(Color.Black, Color.Red, Color.Blue, Color(0xFF00B894), Color(0xFF6C5CE7))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { colors.forEach { c -> Box(modifier = Modifier.size(24.dp).background(c, CircleShape).clickable { viewModel.updateToolSettings(c.toArgb(), currentWidth, tool) }) } }
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(if (isActive) Color(0xFFF1F2F6) else Color.Transparent, CircleShape)) {
        Icon(icon, null, tint = if (isActive) Color.Black else Color.Gray)
    }
}

@Composable
fun PageContainer(page: PageUiModel, uiState: CanvasUiState, viewModel: CanvasViewModel) {
    Box(modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(0.7f).shadow(4.dp).background(Color.White)) {
        BackgroundCanvas(page.background)
        DrawingCanvas(activeTool = uiState.activeTool, strokes = page.strokes, selectedStrokes = emptyList(), dragOffset = Offset.Zero, currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null, lassoPath = emptyList(), onAction = { viewModel.handleMotionEvent(page.page.id, it) })
    }
}

@Composable
fun BackgroundCanvas(backgroundType: PageBackground, modifier: Modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lineColor = Color.LightGray.copy(alpha = 0.3f); val spacing = 40.dp.toPx()
        if (backgroundType == PageBackground.GRID) {
            var y = spacing; while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y)); y += spacing }
            var x = spacing; while (x < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height)); x += spacing }
        }
    }
}