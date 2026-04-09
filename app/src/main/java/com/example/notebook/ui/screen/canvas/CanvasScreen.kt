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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notebook.data.model.Stroke as CanvasStroke

val AuraPastelPalette = listOf(
    Color(0xFF1A1A2E), Color(0xFF2D3436), Color(0xFF636E72),
    Color(0xFFB2BEC3), Color(0xFFDFE6E9), Color(0xFFFFFFFF),
    Color(0xFFFF7675), Color(0xFFE84393), Color(0xFFD63031),
    Color(0xFFFFB8B8), Color(0xFFFFCCD5), Color(0xFFF8BBD9),
    Color(0xFFE17055), Color(0xFFFDCB6E), Color(0xFFF9CA24),
    Color(0xFFFFDDB8), Color(0xFFFFEAB8), Color(0xFFFFF3CD),
    Color(0xFF00B894), Color(0xFF55EFC4), Color(0xFF00CEC9),
    Color(0xFFB8F0E0), Color(0xFFC8F7C5), Color(0xFFD5F5E3),
    Color(0xFF0984E3), Color(0xFF74B9FF), Color(0xFF6C5CE7),
    Color(0xFFBDD5FB), Color(0xFFD6E4FF), Color(0xFFE8EAF6),
    Color(0xFF8E44AD), Color(0xFFA29BFE), Color(0xFFE17055),
    Color(0xFFE8DAEF), Color(0xFFEDE7F6), Color(0xFFFCE4EC)
)

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
    var currentPageIndex by remember(uiState.pages.size) { mutableStateOf(0) }

    LaunchedEffect(uiState.pages.size) {
        if (uiState.pages.isNotEmpty()) {
            currentPageIndex = currentPageIndex.coerceIn(0, uiState.pages.lastIndex)
        }
    }

    val currentPage = uiState.pages.getOrNull(currentPageIndex)

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
    ) { uri ->
        uri?.let {
            currentPage?.let { page ->
                viewModel.addImage(it.toString(), page.page.id)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFFAF8F5)
                ),
                title = {
                    LazyRow(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(32.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .shadow(2.dp, RoundedCornerShape(32.dp)),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Box {
                                ToolButton(Icons.Rounded.Edit, uiState.activeTool == CanvasTool.PEN) {
                                    if (uiState.activeTool == CanvasTool.PEN) {
                                        showSettingsForTool = CanvasTool.PEN
                                    } else {
                                        viewModel.setActiveTool(CanvasTool.PEN)
                                        showSettingsForTool = null
                                    }
                                }
                                if (showSettingsForTool == CanvasTool.PEN) {
                                    ToolSettingsPopup(CanvasTool.PEN, uiState, { showSettingsForTool = null }, viewModel)
                                }
                            }
                        }

                        item {
                            Box {
                                ToolButton(Icons.Rounded.Brush, uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                                    if (uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                                        showSettingsForTool = CanvasTool.HIGHLIGHTER
                                    } else {
                                        viewModel.setActiveTool(CanvasTool.HIGHLIGHTER)
                                        showSettingsForTool = null
                                    }
                                }
                                if (showSettingsForTool == CanvasTool.HIGHLIGHTER) {
                                    ToolSettingsPopup(CanvasTool.HIGHLIGHTER, uiState, { showSettingsForTool = null }, viewModel)
                                }
                            }
                        }

                        item {
                            ToolButton(Icons.Rounded.AutoFixHigh, uiState.activeTool == CanvasTool.ERASER) {
                                viewModel.setActiveTool(CanvasTool.ERASER)
                                showSettingsForTool = null
                            }
                        }

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
                                    if (uiState.activeTool == CanvasTool.SHAPE) {
                                        showSettingsForTool = CanvasTool.SHAPE
                                    } else {
                                        viewModel.setActiveTool(CanvasTool.SHAPE)
                                        showSettingsForTool = null
                                    }
                                }

                                if (showSettingsForTool == CanvasTool.SHAPE) {
                                    ToolSettingsPopup(CanvasTool.SHAPE, uiState, { showSettingsForTool = null }, viewModel)
                                }
                            }
                        }

                        item {
                            Spacer(
                                Modifier.width(1.dp).height(24.dp).background(Color.LightGray)
                            )
                        }

                        item {
                            ToolButton(Icons.Rounded.Gesture, uiState.activeTool == CanvasTool.LASSO) {
                                viewModel.setActiveTool(CanvasTool.LASSO)
                                showSettingsForTool = null
                            }
                        }

                        item {
                            ToolButton(Icons.Rounded.Image, uiState.activeTool == CanvasTool.IMAGE) {
                                if (currentPage != null) {
                                    if (uiState.activeTool == CanvasTool.IMAGE) {
                                        imagePicker.launch("image/*")
                                    } else {
                                        viewModel.setActiveTool(CanvasTool.IMAGE)
                                    }
                                }
                            }
                        }

                        item {
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = uiState.undoStack.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Rounded.Undo,
                                    contentDescription = null,
                                    tint = if (uiState.undoStack.isNotEmpty()) Color(0xFF6C5CE7) else Color.LightGray
                                )
                            }
                        }

                        item {
                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = uiState.redoStack.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Rounded.Redo,
                                    contentDescription = null,
                                    tint = if (uiState.redoStack.isNotEmpty()) Color(0xFF6C5CE7) else Color.LightGray
                                )
                            }
                        }

                        item {
                            IconButton(onClick = { pdfLauncher.launch("${uiState.notebookTitle}.pdf") }) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFFD63031))
                            }
                        }

                        item {
                            IconButton(onClick = { viewModel.showStylusConfigDialog() }) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color(0xFF00B894))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (uiState.hasLassoSelection) {
                        IconButton(onClick = { viewModel.copyLassoSelection() }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        }
                        IconButton(onClick = { viewModel.deleteLassoSelection() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.Red)
                        }
                    }

                    if (uiState.copiedStrokes.isNotEmpty() || uiState.copiedImages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                currentPage?.let { page ->
                                    viewModel.pasteSelection(page.page.id)
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F0FF))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("הקודם")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedPageForTemplate = currentPage.page.id }) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, tint = Color(0xFF6C5CE7))
                        }
                        Text(
                            text = "עמוד ${currentPageIndex + 1} / ${uiState.pages.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF636E72)
                        )
                    }

                    OutlinedButton(
                        onClick = { if (currentPageIndex < uiState.pages.lastIndex) currentPageIndex++ },
                        enabled = currentPageIndex < uiState.pages.lastIndex
                    ) {
                        Text("הבא")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }

                PageContainer(currentPage, uiState, viewModel)

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.addNewPage()
                        currentPageIndex = uiState.pages.size
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("הוסף עמוד חדש")
                }
            }
        }

        if (selectedPageForTemplate != null) {
            TemplateSelectionDialog(
                onDismiss = { selectedPageForTemplate = null },
                onSelect = { bg ->
                    selectedPageForTemplate?.let { pageId ->
                        viewModel.updatePageBackground(pageId, bg)
                    }
                    selectedPageForTemplate = null
                }
            )
        }

        if (uiState.showStylusConfigDialog) {
            StylusConfigDialog(
                currentConfig = uiState.stylusConfig,
                onDismiss = { viewModel.dismissStylusConfigDialog() },
                onSave = { viewModel.setStylusConfig(it) }
            )
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color(0xFF6C5CE7) else Color(0xFF636E72),
            modifier = Modifier.size(24.dp)
        )
    }
}

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
            modifier = Modifier
                .width(340.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFAF8FF)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F2F6))
                        .border()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWid.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val points = if (tool == CanvasTool.SHAPE) {
                                listOf(
                                    StrokePoint(size.width * 0.35f, size.height * 0.35f, 1f),
                                    StrokePoint(size.width * 0.65f, size.height * 0.65f, 1f)
                                )
                            } else {
                                listOf(
                                    StrokePoint(size.width * 0.15f, size.height / 2 + 12, 0.4f),
                                    StrokePoint(size.width * 0.35f, size.height / 2 - 12, 0.8f),
                                    StrokePoint(size.width * 0.55f, size.height / 2 + 8, 1.0f),
                                    StrokePoint(size.width * 0.75f, size.height / 2 - 8, 0.7f),
                                    StrokePoint(size.width * 0.90f, size.height / 2 + 6, 0.5f)
                                )
                            }

                            drawComplexStroke(
                                CanvasStroke(
                                    id = "preview",
                                    points = points,
                                    color = currentColor,
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

                    if (isPen) {
                        Text("סוג עט", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                PenType.BALLPOINT to "Ball Pen",
                                PenType.FOUNTAIN to "Fountain",
                                PenType.CALLIGRAPHY to "Calligraphy"
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

                    if (isHighlighter) {
                        Text("צורת מרקר", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                MarkerShape.ROUND to "עגול",
                                MarkerShape.SQUARE to "מרובע"
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

                    if (tool == CanvasTool.SHAPE) {
                        Text("סוג צורה", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(112.dp)) {
                            items(ShapeType.values().filter { it != ShapeType.FREEHAND }) { shapeType ->
                                FilterChip(
                                    selected = uiState.activeShape == shapeType,
                                    onClick = { viewModel.setShapeMode(shapeType) },
                                    label = {
                                        Text(
                                            shapeType.name.lowercase().replaceFirstChar { ch -> ch.uppercase() },
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        text = "עובי: ${currentWidth.toInt()}px",
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
                    Text("צבע", fontSize = 12.sp, color = Color(0xFF636E72), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(204.dp)) {
                        items(palette) { color ->
                            val isSelected = Color(currentColor) == color
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (isSelected) 3.dp else 0.5.dp,
                                        if (isSelected) Color(0xFF6C5CE7) else Color.White.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    .clickable {
                                        viewModel.updateToolSettings(color.toArgb(), currentWidth, tool)
                                    }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                    ) {
                        Text("סגור")
                    }
                }
            }
        }
    }

    @Composable
    fun PageContainer(
        page: PageUiModel,
        uiState: CanvasUiState,
        viewModel: CanvasViewModel
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .aspectRatio(0.707f)
                .shadow(12.dp, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .clipToBounds()
        ) {
            BackgroundCanvas(page.background)

            DrawingCanvas(
                activeTool = uiState.activeTool,
                strokes = if (uiState.selectionPageId == page.page.id) {
                    page.strokes.filterNot { it.id in uiState.hiddenStrokeIds }
                } else {
                    page.strokes
                },
                selectedStrokes = if (uiState.selectionPageId == page.page.id) uiState.selectedStrokes else emptyList(),
                dragOffset = Offset.Zero,
                currentStroke = if (uiState.drawingPageId == page.page.id) uiState.currentStroke else null,
                lassoPath = if (uiState.drawingPageId == page.page.id || uiState.selectionPageId == page.page.id) {
                    uiState.lassoPath
                } else {
                    emptyList()
                },
                selectionBounds = if (uiState.selectionPageId == page.page.id) uiState.selectionBounds else null,
                onDrawAction = { action, x, y, pressure, isEraser ->
                    viewModel.handleDrawAction(page.page.id, action, x, y, pressure, isEraser)
                }
            )

            page.images.forEach { img ->
                val isSelected = uiState.selectedImages.any { it.id == img.id }
                if (!isSelected) {
                    ResizableDraggableImage(
                        img = img,
                        isActive = uiState.activeTool == CanvasTool.IMAGE
                    ) { nx, ny, nw, nh ->
                        viewModel.updateImageBounds(page.page.id, img.id, nx, ny, nw, nh)
                    }
                }
            }

            if (uiState.selectionPageId == page.page.id) {
                uiState.selectedImages.forEach { img ->
                    ResizableDraggableImage(img = img, isActive = false) { _, _, _, _ -> }
                }
            }
        }
    }

    @Composable
    fun TemplateSelectionDialog(
        onDismiss: () -> Unit,
        onSelect: (PageBackground) -> Unit
    ) {
        val templates = listOf(
            Triple(PageBackground.PLAIN, "ריק", "ללא קווים"),
            Triple(PageBackground.RULED, "מחוקק", "קווים אופקיים"),
            Triple(PageBackground.GRID, "משובץ", "רשת ריבועים"),
            Triple(PageBackground.DOT_GRID, "נקודות", "רשת נקודות"),
            Triple(PageBackground.MUSIC_LINES, "תוים", "קווי תווים")
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("ביטול") }
            },
            title = { Text("בחרי רקע לעמוד", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { (background, title, subtitle) ->
                        Surface(
                            onClick = { onSelect(background) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8F5FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(title, fontWeight = FontWeight.Medium)
                                Text(subtitle, fontSize = 12.sp, color = Color(0xFF636E72))
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun StylusConfigDialog(
        currentConfig: StylusButtonConfig,
        onDismiss: () -> Unit,
        onSave: (StylusButtonConfig) -> Unit
    ) {
        var button1Action by remember { mutableStateOf(currentConfig.button1Action) }
        var button2Action by remember { mutableStateOf(currentConfig.button2Action) }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFAF8FF),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("הגדרות כפתורי עט", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))

                    Text("כפתור 1", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    ActionDropdown(selected = button1Action, onSelect = { button1Action = it })

                    Spacer(Modi)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidSpacer(Modi)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidn = it })

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ביטול")
                        }

                        Button(
                            onClick = {
                                onSave(
                                    StylusButtonConfig(
                                        button1Action = button1Action,
                                        button2Action = button2Action
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                        ) {
                            Text("שמור")
                            )) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidable
                                fun ActionDropdown(
                                    selected: StylusAction,
                                    onSelect: (StylusAction) -> Unit
                                ) {
                                    var expanded by remember { mutableStateOf(false) }

                                    val labels = mapOf(
                                        StylusAction.TOGGLE_PEN_ERASER to "החלף עט/מחק",
                                        StylusAction.UNDO to "Undo",
                                        StylusAction.REDO to "Redo",
                                        StylusAction.CYCLE_PEN_TYPE to "החלף סוג עט",
                                        StylusAction.CYCLE_COLOR to "החלף צבע",
                                        StylusAction.OPEN_PALETTE to "פתח פלטה",
                                        StylusAction.NONE to "ללא פעולה"
                                    )

                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = labels[selected] ?: selected.name,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            StylusAction.values().forEach { action ->
                                                DropdownMenuItem(
                                                    text = { Text(labels[action] ?: action.name) },
                                                    onClick = {
                                                        onSelect(action)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                @Composable
                                fun ResizableDraggableImage(
                                    img: CanvasImage,
                                    isActive: Boolean,
                                    onUpdate: (Float, Float, Float, Float) -> )) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidmageBitmap?>(null) }
                            var x by remember(img.id) { mutableStateOf(img.x) }
                            var y by remember(img.id) { mutableStateOf(img.y) }
                            var w by remember(img.id) { mutableStateOf(img.width) }
                            var h by remember(img.id) { mutableStateOf(img.heigh)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWid         .fillMaxWidroid.net.Uri.parse(img.uri)
                                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                        android.graphics.BitmapFactory.decodeStream(stream)?.let {
                                            bitmap = it.asImageBitm)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWid   }
                                        }

                                        Box(modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) {
                                            Box(
                                                modifier = Modifier
                                                    .size(
                                                        with(LocalDensity.current) { w.toDp() },
                                                        with(LocalDen)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWid      if (isActive) {
                                                                detectDragGestures(
                                                                    onDragEnd = { onUpdate(x, y, w, h) }
                                                                ) { change, dragAmount ->
                                                                    change.consume()
                                                                    x += dragAm)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWid                }
                                                                    ) {
                                                                    bitmap?.let {
                                                                        Image(
                                                                            bitmap = it,
                                                                            contentDescription = null,
                                                                            contentScale = ContentScale.FillBou)) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .fillMaxWid   if (isActive) {
                                                                                Box(
                                                                                    modifier = Modi)) {
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .fillMaxWidRou)) {
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .fillMaxWidr
                                                                                            .size(24)) {
                                                                                        Box(
                                                                                            modifier = Modifier
                                                                                                .fillMaxWidFF6C5CE7), RoundedCornerShape(4.)) {
                                                                                        Box(
                                                                                            modifier = Modifier
                                                                                                .fillMaxWid                              onDragEnd = { onUpdate(x, y, w, )) {
                                                                                                Box(
                                                                                                    modifier = Modifier
                                                                                                        .fillMaxWidsume()
                                                                                                            w = (w + dragAmount.x).coerceAtLeas)) {
                                                                                                Box(
                                                                                                    modifier = Modifier
                                                                                                        .fillMaxWidx(
                                                                                                            modifier = Modifier
                                                                                                                .fillMaxWid    }
                                                                                            }

                                                                                                @Composable
                                                                                                fun BackgroundCanvas(
                                                                                                    backgroundType: PageBackground,
                                                                                                    modifier: Modifier = Modifier.fillMaxSi)) {
                                                                                                    Box(
                                                                                                        modifier = Modifier
                                                                                                            .fillMaxWid0xFFB2BEC3).copy(alp)) {
                                                                                                    Box(
                                                                                                        modifier = Modifier
                                                                                                            .fillMaxWidifier
                                                                                                            .fillMaxWidntY = spacing
                                                                                                        while (currentY < size.height) {
                                                                                                            drawLine(lineColor, Of)) {
                                                                                                                Box(
                                                                                                                    modifier = Modifier
                                                                                                                        .fillMaxWid           Box(
                                                                                                                            modifier = Modifier
                                                                                                                                .fillMaxWidtY = spacing
                                                                                                                    while (currentY < size.he)) {
                                                                                                                    Box(
                                                                                                                        modifier = Modifier
                                                                                                                            .fillMaxWid)) {
                                                                                                                    Box(
                                                                                                                        modifier = Modifier
                                                                                                                            .fillMaxWid
                                                                                                                        while (currentX < size.widt)) {
                                                                                                                        Box(
                                                                                                                            modifier = Modifier
                                                                                                                                .fillMaxWid {
                                                                                                                                    Box(
                                                                                                                                        modifier = Modifier
                                                                                                                                            .fillMaxWidund.DOT_GRID -> {
                                                                                                                                    var curr)) {
                                                                                                                                    Box(
                                                                                                                                        modifier = Modifier
                                                                                                                                            .fillMaxWidacing
                                                                                                                                        while (currentX < size.widt)) {
                                                                                                                                        Box(
                                                                                                                                            modifier = Modifier
                                                                                                                                                .fillMaxWid                        currentX += spa)) {
                                                                                                                                        Box(
                                                                                                                                            modifier = Modifier
                                                                                                                                                .fillMaxWid           PageBackground.MUSIC_LINES -> {
                                                                                                                                        val staffSpacing = 12.dp.)) {
                                                                                                                                        Box(
                                                                                                                                            modifier = Modifier
                                                                                                                                                .fillMaxWidg * 4) {
                                                                                                                                            for (i in)) {
                                                                                                                                            Box(
                                                                                                                                                modifier = Modifier
                                                                                                                                                    .fillMaxWid                 start = Offset(16.dp.toPx(), s)) {
                                                                                                                                            Box(
                                                                                                                                                modifier = Modifier
                                                                                                                                                    .fillMaxWid +)) {
                                                                                                                                            Box(
                                                                                                                                                modifier = Modifier
                                                                                                                                                    .fillMaxWid            }
                                                                                                                                            staffY += staffSpacing * 4 + spacing )) {
                                                                                                                                            Box(
                                                                                                                                                modifier = Modifier
                                                                                                                                                    .fillMaxWid`

                                                                                                                                            ## למה זה אמור לפתור את הבע)) {
                                                                                                                                            Box(
                                                                                                                                                modifier = Modifier
                                                                                                                                                    .fillMaxWid= Modifier
                                                                                                                                                    .fillMaxWid                   .fillMaxWid Modifier
                                                                                                                                                        .fillMaxWid                 .fillMaxWid   Box(
                                                                                                                                                    modifier = Modifier
                                                                                                                                                        .fillMaxWid                  .fillMaxWid       modifier = Modifier
                                                                                                                                                            .fillMaxWid                      .fillMaxWid  .fillMaxWidmodifier = Modifier
                                                                                                                                                            .fillMaxWidifier
                                                                                                                                                            .fillMaxWid Modifier
                                                                                                                                                        .fillMaxWidifier
                                                                                                                                                        .fillMaxWidxWid