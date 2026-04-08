package com.example.notebook.ui.screen.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.PageBackground
import com.example.notebook.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    notebookId: Long,
    onBack: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showToolSettings by remember { mutableStateOf(false) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.notebookTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                        if (uiState.activeTool == CanvasTool.PEN) showToolSettings = true
                        else viewModel.setActiveTool(CanvasTool.PEN)
                    }
                    ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                        if (uiState.activeTool == CanvasTool.HIGHLIGHTER) showToolSettings = true
                        else viewModel.setActiveTool(CanvasTool.HIGHLIGHTER)
                    }
                    ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) {
                        viewModel.setActiveTool(CanvasTool.ERASER)
                    }
                }
            )
        }
    ) { padding ->
        if (showToolSettings) {
            val currentToolColor = if (uiState.activeTool == CanvasTool.HIGHLIGHTER) uiState.highlighterColor else uiState.penColor
            val currentToolWidth = if (uiState.activeTool == CanvasTool.HIGHLIGHTER) uiState.highlighterWidth else uiState.penWidth

            ColorPickerAndWidthDialog(
                initialColor = Color(currentToolColor),
                initialWidth = currentToolWidth,
                isHighlighter = uiState.activeTool == CanvasTool.HIGHLIGHTER,
                onDismiss = { showToolSettings = false },
                onSave = { color, width ->
                    viewModel.updateToolSettings(color.toArgb(), width)
                    showToolSettings = false
                }
            )
        }

        if (selectedPageForTemplate != null) {
            TemplateSelectionDialog(
                onDismiss = { selectedPageForTemplate = null },
                onSelect = {
                    viewModel.updatePageBackground(selectedPageForTemplate!!, it)
                    selectedPageForTemplate = null
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(PastelBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(uiState.pages, key = { it.page.id }) { pageModel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { selectedPageForTemplate = pageModel.page.id }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Page Settings", tint = ToolbarIcon)
                    }
                    Box(
                        modifier = Modifier
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .shadow(8.dp, RoundedCornerShape(4.dp))
                            .background(Color.White)
                    ) {
                        DrawingCanvas(
                            strokes = pageModel.strokes,
                            currentStroke = if (uiState.drawingPageId == pageModel.page.id) uiState.currentStroke else null,
                            backgroundType = pageModel.background,
                            onAction = { event -> viewModel.handleMotionEvent(pageModel.page.id, event) }
                        )
                    }
                }
            }
            item {
                Button(onClick = { viewModel.addNewPage() }, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Page")
                }
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = if (isActive) Modifier.background(PastelSurface, CircleShape) else Modifier
    ) {
        Icon(icon, contentDescription = null, tint = if (isActive) ToolbarIconActive else ToolbarIcon)
    }
}

@Composable
fun ColorPickerAndWidthDialog(
    initialColor: Color,
    initialWidth: Float,
    isHighlighter: Boolean,
    onDismiss: () -> Unit,
    onSave: (Color, Float) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var width by remember { mutableStateOf(initialWidth) }

    // יצירת פלטת הצבעים הסטנדרטית בדומה לתמונה ששלחת
    val colorPalette = remember {
        val grid = mutableListOf<Color>()

        // שורת גווני אפור
        val grays = listOf(
            0xFFFFFFFF, 0xFFE0E0E0, 0xFFCCCCCC, 0xFFB3B3B3, 0xFF999999,
            0xFF808080, 0xFF666666, 0xFF4D4D4D, 0xFF333333, 0xFF1A1A1A, 0xFF000000
        )
        grid.addAll(grays.map { Color(it) })

        // 6 שורות של קשת צבעים
        val lightnessLevels = listOf(
            Pair(0.15f, 1.0f),
            Pair(0.35f, 1.0f),
            Pair(0.55f, 1.0f),
            Pair(1.0f, 1.0f),
            Pair(1.0f, 0.75f),
            Pair(1.0f, 0.5f)
        )
        val hues = listOf(180f, 200f, 220f, 260f, 300f, 330f, 0f, 30f, 60f, 90f, 120f)

        for (level in lightnessLevels) {
            for (h in hues) {
                grid.add(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, level.first, level.second))))
            }
        }
        grid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(selectedColor, width) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(if (isHighlighter) "Marker Settings" else "Pen Settings") },
        text = {
            Column(horizontalAlignment = Alignment.Start) {

                // --- אזור בחירת העובי ---
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isHighlighter) "Marker thickness" else "Pen thickness", fontSize = 14.sp, color = PastelOnBackground)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (width > 1f) width -= 1f }) {
                        Text("-", fontSize = 24.sp)
                    }
                    Text("${String.format("%.1f", width)}px", fontSize = 14.sp)
                    IconButton(onClick = { if (width < 60f) width += 1f }) {
                        Text("+", fontSize = 24.sp)
                    }
                }
                Slider(
                    value = width,
                    onValueChange = { width = it },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = ToolbarIconActive,
                        activeTrackColor = ToolbarIconActive.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- אזור הצבעים ---
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Standard colors", fontSize = 14.sp, color = PastelOnBackground)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Colorize, contentDescription = "Eyedropper", tint = PastelOnBackground, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(11), // 11 עמודות בדיוק כמו בתמונה
                    modifier = Modifier.fillMaxWidth().height(220.dp).border(1.dp, Color.LightGray)
                ) {
                    items(colorPalette) { color ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f) // שומר על הריבועים שיהיו מדויקים
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 2.dp else 0.dp,
                                    color = if (selectedColor == color) Color.Black else Color.Transparent
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Select Page Template") },
        text = {
            Column {
                PageBackground.values().forEach { type ->
                    TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(when(type) {
                            PageBackground.PLAIN -> "Plain"
                            PageBackground.LINES -> "Lines"
                            PageBackground.GRID -> "Grid"
                            PageBackground.DOTS -> "Dots"
                        })
                    }
                }
            }
        }
    )
}