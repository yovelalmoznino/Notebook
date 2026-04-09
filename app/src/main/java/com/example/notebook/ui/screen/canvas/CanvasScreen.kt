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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.*
import com.example.notebook.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notebook.data.model.Stroke as CanvasStroke

// ── Pastel color palette (36 colors) ──────────────────────────────────
val AuraPastelPalette = listOf(
    // Row 1 – blacks, grays, whites
    Color(0xFF1A1A2E), Color(0xFF2D3436), Color(0xFF636E72),
    Color(0xFFB2BEC3), Color(0xFFDFE6E9), Color(0xFFFFFFFF),
    // Row 2 – pinks & reds
    Color(0xFFFF7675), Color(0xFFE84393), Color(0xFFD63031),
    Color(0xFFFFB8B8), Color(0xFFFFCCD5), Color(0xFFF8BBD9),
    // Row 3 – oranges & yellows
    Color(0xFFE17055), Color(0xFFFDCB6E), Color(0xFFF9CA24),
    Color(0xFFFFDDB8), Color(0xFFFFEAB8), Color(0xFFFFF3CD),
    // Row 4 – greens
    Color(0xFF00B894), Color(0xFF55EFC4), Color(0xFF00CEC9),
    Color(0xFFB8F0E0), Color(0xFFC8F7C5), Color(0xFFD5F5E3),
    // Row 5 – blues
    Color(0xFF0984E3), Color(0xFF74B9FF), Color(0xFF6C5CE7),
    Color(0xFFBDD5FB), Color(0xFFD6E4FF), Color(0xFFE8EAF6),
    // Row 6 – purples & special
    Color(0xFF8E44AD), Color(0xFFA29BFE), Color(0xFFE17055),
    Color(0xFFE8DAEF), Color(0xFFEDE7F6), Color(0xFFFCE4EC)
)

// ── Highlighter palette ────────────────────────────────────────────────
val AuraHighlighterPalette = listOf(
    Color(0x66FDCB6E), Color(0x66FAB1A0), Color(0x66A8EDEA),
    Color(0x6681ECEC), Color(0x6655EFC4), Color(0x66FD79A8),
    Color(0x6674B9FF), Color(0x66B2FF59), Color(0x66FFCCBC),
    Color(0x66CE93D8), Color(0x66FFE082), Color(0x66EF9A9A)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    notebookId: Long,
    onBack: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettingsForTool by remember { mutableStateOf<CanvasTool?>(null) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            viewModel.exportToPdf(context, it) {
                Toast.makeText(context, "✅ PDF ייוצא בהצלחה!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.addImage(it.toString()) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFFAF8F5)
                ),
                title = {
                    // ── Tool bar ──────────────────────────────────────
                    LazyRow(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(32.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .shadow(2.dp, RoundedCornerShape(32.dp)),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // PEN
                        item {
                            Box {
                                ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                                    if (uiState.activeTool == CanvasTool.PEN)
                                        showSettingsForTool = CanvasTool.PEN
                                    else { viewModel.setActiveTool(CanvasTool.PEN); showSettingsForTool = null }
                                }
                                if (showSettingsForTool == CanvasTool.PEN)
                                    ToolSettingsPopup(CanvasTool.PEN, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }
                        // HIGHLIGHTER
                        item {
                            Box {
                                ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                                    if (uiState.activeTool == CanvasTool.HIGHLIGHTER)
                                        showSettingsForTool = CanvasTool.HIGHLIGHTER
                                    else { viewModel.setActiveTool(CanvasTool.HIGHLIGHTER); showSettingsForTool = null }
                                }
                                if (showSettingsForTool == CanvasTool.HIGHLIGHTER)
                                    ToolSettingsPopup(CanvasTool.HIGHLIGHTER, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }
                        // ERASER
                        item {
                            ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) {
                                viewModel.setActiveTool(CanvasTool.ERASER); showSettingsForTool = null
                            }
                        }
                        // SHAPE
                        item {
                            Box {
                                val shapeIcon = when (uiState.activeShape) {
                                    ShapeType.STAR -> Icons.Rounded.Star
                                    ShapeType.TRIANGLE -> Icons.Rounded.ChangeHistory
                                    ShapeType.RECTANGLE -> Icons.Rounded.Square
                                    ShapeType.CIRCLE -> Icons.Rounded.Circle
                                    ShapeType.ARROW -> Icons.Rounded.TrendingFlat
                                    else -> Icons.Rounded.Category
                                }
                                ToolButton(shapeIcon, uiState.activeTool == CanvasTool.SHAPE) {
                                    if (uiState.activeTool == CanvasTool.SHAPE)
                                        showSettingsForTool = CanvasTool.SHAPE
                                    else { viewModel.setActiveTool(CanvasTool.SHAPE); showSettingsForTool = null }
                                }
                                if (showSettingsForTool == CanvasTool.SHAPE)
                                    ToolSettingsPopup(CanvasTool.SHAPE, uiState, { showSettingsForTool = null }, viewModel)
                            }
                        }
                        // divider
                        item { Spacer(Modifier.width(1.dp).height(24.dp).background(Color.LightGray)) }
                        // LASSO
                        item {
                            ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) {
                                viewModel.setActiveTool(CanvasTool.LASSO); showSettingsForTool = null
                            }
                        }
                        // IMAGE
                        item {
                            ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) {
                                if (uiState.activeTool == CanvasTool.IMAGE) imagePicker.launch("image/*")
                                else viewModel.setActiveTool(CanvasTool.IMAGE)
                            }
                        }
                        // UNDO / REDO
                        item {
                            IconButton(onClick = { viewModel.undo() }, enabled = uiState.undoStack.isNotEmpty()) {
                                Icon(Icons.Rounded.Undo, null, tint = if (uiState.undoStack.isNotEmpty()) Color(0xFF6C5CE7) else Color.LightGray)
                            }
                        }
                        item {
                            IconButton(onClick = { viewModel.redo() }, enabled = uiState.redoStack.isNotEmpty()) {
                                Icon(Icons.Rounded.Redo, null, tint = if (uiState.redoStack.isNotEmpty()) Color(0xFF6C5CE7) else Color.LightGray)
                            }
                        }
                        // PDF export
                        item {
                            IconButton(onClick = { pdfLauncher.launch("${uiState.notebookTitle}.pdf") }) {
                                Icon(Icons.Rounded.PictureAsPdf, null, tint = Color(0xFFD63031))
                            }
                        }
                        // Stylus config
                        item {
                            IconButton(onClick = { viewModel.showStylusConfigDialog() }) {
                                Icon(Icons.Rounded.Edit, null, tint = Color(0xFF00B894))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
                },
                actions = {
                    if (uiState.hasLassoSelection) {
                        IconButton(onClick = { viewModel.copyLassoSelection() }) {
                            Icon(Icons.Rounded.ContentCopy, null)
                        }
                        IconButton(onClick = { viewModel.deleteLassoSelection() }) {
                            Icon(Icons.Rounded.Delete, null, tint = Color.Red)
                        }
                    }
                    if (uiState.copiedStrokes.isNotEmpty() || uiState.copiedImages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.pasteSelection(uiState.pages.firstOrNull()?.page?.id ?: 0L) }) {
                            Icon(Icons.Rounded.ContentPaste, null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F0FF)),
            userScrollEnabled = !uiState.isDrawing
        ) {
            items(uiState.pages, key = { it.page.id }) { pageModel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedPageForTemplate = pageModel.page.id }) {
                            Icon(Icons.Rounded.GridView, null, tint = Color(0xFF6C5CE7))
                        }
                        Text(
                            "עמוד ${uiState.pages.indexOf(pageModel) + 1}",
                            fontSize = 12.sp,
                            color = Color(0xFF636E72)
                        )
                    }
                    PageContainer(pageModel, uiState, viewModel)
                }
            }
            item {
                Button(
                    onClick = { viewModel.addNewPage() },
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("הוסף עמוד חדש")
                }
            }
        }

        // Template dialog
        if (selectedPageForTemplate != null) {
            TemplateSelectionDialog(
                onDismiss = { selectedPageForTemplate = null },
                onSelect = {
                    viewModel.updatePageBackground(selectedPageForTemplate!!, it)
                    selectedPageForTemplate = null
                }
            )
        }

        // Stylus config dialog
        if (uiState.showStylusConfigDialog) {
            StylusConfigDialog(
                currentConfig = uiState.stylusConfig,
                onDismiss = { viewModel.dismissStylusConfigDialog() },
                onSave = { viewModel.setStylusConfig(it) }
            )
        }
    }
}

// ── Tool Settings Popup ────────────────────────────────────────────────
@Composable
fun ToolSettingsPopup(
    tool: CanvasTool,
    uiState: CanvasUiState,
    onDismiss: () -> Unit,
    viewModel: CanvasViewModel
) {
    val isPen = tool == CanvasTool.PEN
    val isHighlighter = tool == CanvasTool.HIGHLIGHTER
    val palette = if (isHighlighter) AuraHighlighterPalette else AuraPastelPalette
    val currentColor = when (tool) {
        CanvasTool.HIGHLIGHTER -> uiState.highlighterColor
        CanvasTool.SHAPE -> uiState.shapeColor
        else -> uiState.penColor
    }
    val currentWidth = when (tool) {
        CanvasTool.HIGHLIGHTER -> uiState.highlighterWidth
        CanvasTool.SHAPE -> uiState.shapeWidth
        else -> uiState.penWidth
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 160),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier.width(340.dp).shadow(12.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFAF8FF)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Preview stroke
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F2F6))
                        .border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = if (tool == CanvasTool.SHAPE)
                            listOf(StrokePoint(size.width * 0.35f, size.height * 0.35f, 1f), StrokePoint(size.width * 0.65f, size.height * 0.65f, 1f))
                        else
                            listOf(
                                StrokePoint(size.width * 0.15f, size.height / 2 + 12, 0.4f),
                                StrokePoint(size.width * 0.35f, size.height / 2 - 12, 0.8f),
                                StrokePoint(size.width * 0.55f, size.height / 2 + 8,  1.0f),
                                StrokePoint(size.width * 0.75f, size.height / 2 - 8,  0.7f),
                                StrokePoint(size.width * 0.90f, size.height / 2 + 6,  0.5f)
                            )
                        drawComplexStroke(
                            CanvasStroke(
                                points = points, color = currentColor,
                                strokeWidth = currentWidth.coerceAtMost(30f),
                                isHighlighter = isHighlighter,
                                penType = uiState.activePenType,
                                markerShape = uiState.activeMarkerShape,
                                shapeType = if (tool == CanvasTool.SHAPE) uiState.activeShape else ShapeType.FREEHAND
                            ),
                            Offset.Zero
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Pen type chips
                if (isPen) {
                    Text("סוג עט", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PenType.BALLPOINT to "🖊 Ball Pen",
                            PenType.FOUNTAIN  to "✒ Fountain",
                            PenType.CALLIGRAPHY to "📜 Calligraphy"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = uiState.activePenType == type,
                                onClick = { viewModel.setPenType(type) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Marker shape chips
                if (isHighlighter) {
                    Text("צורת מרקר", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            MarkerShape.ROUND  to "🔵 עגול",
                            MarkerShape.SQUARE to "🟦 מרובע"
                        ).forEach { (shape, label) ->
                            FilterChip(
                                selected = uiState.activeMarkerShape == shape,
                                onClick = { viewModel.setMarkerShape(shape) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Shape type grid
                if (tool == CanvasTool.SHAPE) {
                    Text("סוג צורה", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(112.dp)) {
                        items(ShapeType.values().filter { it != ShapeType.FREEHAND }) {
                            FilterChip(
                                selected = uiState.activeShape == it,
                                onClick = { viewModel.setShapeMode(it) },
                                label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Width slider
                Text(
                    "עובי: ${currentWidth.toInt()}px",
                    fontSize = 12.sp,
                    color = Color(0xFF636E72),
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = currentWidth,
                    onValueChange = { viewModel.updateToolSettings(currentColor, it, tool) },
                    valueRange = if (isHighlighter) 10f..80f else 1f..40f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6C5CE7),
                        activeTrackColor = Color(0xFF6C5CE7).copy(alpha = 0.6f)
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Color palette (36 colors, 6 per row)
                Text("צבע", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(204.dp)) {
                    items(palette) { c ->
                        val isSelected = Color(currentColor) == c
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (isSelected) 3.dp else 0.5.dp,
                                    if (isSelected) Color(0xFF6C5CE7) else Color.White.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .clickable { viewModel.updateToolSettings(c.toArgb(), currentWidth, tool) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) { Text("סגור") }
            }
        }
    }
}

// ── Tool Button ────────────────────────────────────────────────────────
@Composable
fun ToolButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .background(
                if (isActive) Color(0xFFEDE7F6) else Color.Transparent,
                CircleShape
            )
            .border(
                if (isActive) 1.5.dp else 0.dp,
                if (isActive) Color(0xFF6C5CE7) else Color.Transparent,
                CircleShape
            )
    ) {
        Icon(icon, null, tint = if (isActive) Color(0xFF6C5CE7) else Color(0xFF636E72), modifier = Modifier.size(24.dp))
    }
}

// ── Page Container ─────────────────────────────────────────────────────
@Composable
fun PageContainer(page: PageUiModel, uiState: CanvasUiState, viewModel: CanvasViewModel) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(0.707f)  // A4 ratio
            .shadow(12.dp, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .clipToBounds()
    ) {
        BackgroundCanvas(page.background)
        DrawingCanvas(
            activeTool = uiState.activeTool,
            strokes = if (uiState.selectionPageId == page.page.id)
                page.strokes.filterNot { it.id in uiState.hiddenStrokeIds } else page.strokes,
            selectedStrokes = if (uiState.selectionPageId == page.page.id) uiState.selectedStrokes else emptyList(),
            dragOffset = Offset.Zero,
            currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null,
            lassoPath = if (uiState.drawingPageId == page.page.id || uiState.selectionPageId == page.page.id) uiState.lassoPath else emptyList(),
            selectionBounds = if (uiState.selectionPageId == page.page.id) uiState.selectionBounds else null,
            onDrawAction = { action, x, y, pressure, isEraser ->
                viewModel.handleDrawAction(page.page.id, action, x, y, pressure, isEraser)
            }
        )
        page.images.forEach { img ->
            val isSelected = uiState.selectedImages.any { it.id == img.id }
            if (!isSelected) {
                ResizableDraggableImage(img, uiState.activeTool == CanvasTool.IMAGE) { nx, ny, nw, nh ->
                    viewModel.updateImageBounds(page.page.id, img.id, nx, ny, nw, nh)
                }
            }
        }
        // Show selected images during lasso
        if (uiState.selectionPageId == page.page.id) {
            uiState.selectedImages.forEach { img ->
                ResizableDraggableImage(img, false) { _, _, _, _ -> }
            }
        }
    }
}

// ── Template Selection Dialog ──────────────────────────────────────────
@Composable
fun TemplateSelectionDialog(onDismiss: () -> Unit, onSelect: (PageBackground) -> Unit) {
    val templates = mapOf(
        PageBackground.PLAIN      to ("📄 ריק" to "ללא קווים"),
        PageBackground.RULED      to ("📝 מחוקק" to "קווים אופקיים"),
        PageBackground.GRID       to ("🔲 משובץ" to "רשת ריבועים"),
        PageBackground.DOT_GRID   to ("⬛ נקודות" to "רשת נקודות"),
        PageBackground.MUSIC_LINES to ("🎵 תוים" to "קווי תוים"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
        title = { Text("בחר רקע לעמוד", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { (bg, info) ->
                    Surface(
                        onClick = { onSelect(bg) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8F5FF),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(info.first, fontSize = 24.sp)
                            Column {
                                Text(info.first.drop(2), fontWeight = FontWeight.Medium)
                                Text(info.second, fontSize = 12.sp, color = Color(0xFF636E72))
                            }
                        }
                    }
                }
            }
        }
    )
}

// ── Stylus Config Dialog ───────────────────────────────────────────────
@Composable
fun StylusConfigDialog(
    currentConfig: StylusButtonConfig,
    onDismiss: () -> Unit,
    onSave: (StylusButtonConfig) -> Unit
) {
    var b1 by remember { mutableStateOf(currentConfig.button1Action) }
    var b2 by remember { mutableStateOf(currentConfig.button2Action) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFAF8FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("⚙️ הגדרות כפתורי עט", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("תומך בסמסונג S Pen, לנובו, ועטים אנדרואיד כלליים", fontSize = 12.sp, color = Color(0xFF636E72))
                Spacer(Modifier.height(20.dp))

                Text("כפתור עיקרי (1)", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ActionDropdown(selected = b1, onSelect = { b1 = it })

                Spacer(Modifier.height(16.dp))

                Text("כפתור משני (2)", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ActionDropdown(selected = b2, onSelect = { b2 = it })

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("ביטול") }
                    Button(
                        onClick = { onSave(StylusButtonConfig(b1, b2)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                    ) { Text("שמור") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDropdown(selected: StylusAction, onSelect: (StylusAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        StylusAction.TOGGLE_PEN_ERASER to "🔄 החלף עט/מחק",
        StylusAction.UNDO to "↩️ ביטול (Undo)",
        StylusAction.REDO to "↪️ חזרה (Redo)",
        StylusAction.CYCLE_PEN_TYPE to "✒️ החלף סוג עט",
        StylusAction.CYCLE_COLOR to "🎨 החלף צבע",
        StylusAction.OPEN_PALETTE to "🎨 פתח פלטת צבעים",
        StylusAction.NONE to "⚫ ללא פעולה"
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labels[selected] ?: selected.name,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StylusAction.values().forEach { action ->
                DropdownMenuItem(
                    text = { Text(labels[action] ?: action.name) },
                    onClick = { onSelect(action); expanded = false }
                )
            }
        }
    }
}

// ── Resizable Draggable Image ──────────────────────────────────────────
@Composable
fun ResizableDraggableImage(
    img: CanvasImage,
    isActive: Boolean,
    onUpdate: (Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(img.id) { mutableStateOf<ImageBitmap?>(null) }
    var x by remember(img.id) { mutableStateOf(img.x) }
    var y by remember(img.id) { mutableStateOf(img.y) }
    var w by remember(img.id) { mutableStateOf(img.width) }
    var h by remember(img.id) { mutableStateOf(img.height) }

    LaunchedEffect(img.uri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(img.uri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.let {
                        bitmap = it.asImageBitmap()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) {
        Box(
            modifier = Modifier
                .size(
                    with(LocalDensity.current) { w.toDp() },
                    with(LocalDensity.current) { h.toDp() }
                )
                .pointerInput(isActive) {
                    if (isActive) detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount ->
                        change.consume()
                        x += dragAmount.x
                        y += dragAmount.y
                    }
                }
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isActive) {
                Box(modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFF6C5CE7), RoundedCornerShape(4.dp)))
                // Resize handle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color(0xFF6C5CE7), RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { onUpdate(x, y, w, h) }) { change, dragAmount ->
                                change.consume()
                                w = (w + dragAmount.x).coerceAtLeast(80f)
                                h = (h + dragAmount.y).coerceAtLeast(80f)
                            }
                        }
                )
            }
        }
    }
}

// ── Background Canvas ──────────────────────────────────────────────────
@Composable
fun BackgroundCanvas(backgroundType: PageBackground, modifier: Modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lineColor = Color(0xFFB2BEC3).copy(alpha = 0.4f)
        val spacing = 40.dp.toPx()
        when (backgroundType) {
            PageBackground.RULED -> {
                var curY = spacing
                while (curY < size.height) {
                    drawLine(lineColor, Offset(0f, curY), Offset(size.width, curY), strokeWidth = 0.8f)
                    curY += spacing
                }
            }
            PageBackground.GRID -> {
                var curY = spacing
                while (curY < size.height) {
                    drawLine(lineColor, Offset(0f, curY), Offset(size.width, curY), strokeWidth = 0.8f); curY += spacing
                }
                var curX = spacing
                while (curX < size.width) {
                    drawLine(lineColor, Offset(curX, 0f), Offset(curX, size.height), strokeWidth = 0.8f); curX += spacing
                }
            }
            PageBackground.DOT_GRID -> {
                var curY = spacing
                while (curY < size.height) {
                    var curX = spacing
                    while (curX < size.width) {
                        drawCircle(lineColor, radius = 2f, center = Offset(curX, curY))
                        curX += spacing
                    }
                    curY += spacing
                }
            }
            PageBackground.MUSIC_LINES -> {
                val staffSpacing = 12.dp.toPx()
                var staffY = spacing
                while (staffY < size.height - staffSpacing * 4) {
                    for (i in 0..4) {
                        drawLine(lineColor, Offset(16.dp.toPx(), staffY + i * staffSpacing), Offset(size.width - 16.dp.toPx(), staffY + i * staffSpacing), strokeWidth = 0.8f)
                    }
                    staffY += staffSpacing * 4 + spacing * 1.5f
                }
            }
            else -> { /* PLAIN – no lines */ }
        }
    }
}