package com.example.notebook.ui.screen.canvas

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.data.model.CanvasImage
import com.example.notebook.data.model.CanvasTool
import com.example.notebook.data.model.MarkerShape
import com.example.notebook.data.model.PageBackground
import com.example.notebook.data.model.PageUiModel
import com.example.notebook.data.model.PenType
import com.example.notebook.data.model.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput

private val PenPalette = listOf(
    Color(0xFF1A1A1A),
    Color(0xFF2D3436),
    Color(0xFF636E72),
    Color(0xFFD63031),
    Color(0xFFE17055),
    Color(0xFFFDCB6E),
    Color(0xFF00B894),
    Color(0xFF00CEC9),
    Color(0xFF0984E3),
    Color(0xFF6C5CE7),
    Color(0xFFA29BFE),
    Color(0xFFE84393)
)

private val HighlighterPalette = listOf(
    Color(0x66FFF59D),
    Color(0x66FFE082),
    Color(0x66FFCCBC),
    Color(0x66F8BBD0),
    Color(0x66DCEDC8),
    Color(0x66B2DFDB),
    Color(0x66BBDEFB),
    Color(0x66D1C4E9)
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

    var currentPageIndex by remember(uiState.pages.size) { mutableIntStateOf(0) }
    var selectedPageForTemplate by remember { mutableStateOf<Long?>(null) }
    var showPenSettings by remember { mutableStateOf(false) }
    var showHighlighterSettings by remember { mutableStateOf(false) }

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
                Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && currentPage != null) {
            viewModel.addImage(uri.toString(), currentPage.page.id)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.notebookTitle.ifBlank { "Aura Scribble" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { pdfLauncher.launch("${uiState.notebookTitle.ifBlank { "notebook" }}.pdf") }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF8F5FF)
                )
            )
        },
        containerColor = Color(0xFFF3EEFF)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
        ) {
            ToolRow(
                activeTool = uiState.activeTool,
                onPenClick = {
                    if (uiState.activeTool == CanvasTool.PEN) {
                        showPenSettings = true
                    } else {
                        viewModel.setActiveTool(CanvasTool.PEN)
                    }
                },
                onHighlighterClick = {
                    if (uiState.activeTool == CanvasTool.HIGHLIGHTER) {
                        showHighlighterSettings = true
                    } else {
                        viewModel.setActiveTool(CanvasTool.HIGHLIGHTER)
                    }
                },
                onEraserClick = { viewModel.setActiveTool(CanvasTool.ERASER) },
                onLassoClick = { viewModel.setActiveTool(CanvasTool.LASSO) },
                onImageClick = {
                    viewModel.setActiveTool(CanvasTool.IMAGE)
                    imagePicker.launch("image/*")
                },
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                onPasteClick = {
                    currentPage?.let { viewModel.pasteSelection(it.page.id) }
                },
                onCopyClick = { viewModel.copyLassoSelection() },
                onDeleteSelectionClick = { viewModel.deleteLassoSelection() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (currentPage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedPageForTemplate = currentPage.page.id }) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, tint = Color(0xFF6C5CE7))
                        }
                        Text(
                            text = "Page ${currentPageIndex + 1} / ${uiState.pages.size}",
                            color = Color(0xFF5F5A74)
                        )
                    }

                    OutlinedButton(
                        onClick = { if (currentPageIndex < uiState.pages.lastIndex) currentPageIndex++ },
                        enabled = currentPageIndex < uiState.pages.lastIndex
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SinglePageCanvas(
                    page = currentPage,
                    uiState = uiState,
                    viewModel = viewModel
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add page")
                }
            }
        }
    }

    if (showPenSettings) {
        PenSettingsDialog(
            currentColor = uiState.penColor,
            currentWidth = uiState.penWidth,
            currentPenType = uiState.activePenType,
            onDismiss = { showPenSettings = false },
            onColorChange = { color -> viewModel.updateToolSettings(color, uiState.penWidth, CanvasTool.PEN) },
            onWidthChange = { width -> viewModel.updateToolSettings(uiState.penColor, width, CanvasTool.PEN) },
            onPenTypeChange = { penType -> viewModel.setPenType(penType) }
        )
    }

    if (showHighlighterSettings) {
        HighlighterSettingsDialog(
            currentColor = uiState.highlighterColor,
            currentWidth = uiState.highlighterWidth,
            currentShape = uiState.activeMarkerShape,
            onDismiss = { showHighlighterSettings = false },
            onColorChange = { color -> viewModel.updateToolSettings(color, uiState.highlighterWidth, CanvasTool.HIGHLIGHTER) },
            onWidthChange = { width -> viewModel.updateToolSettings(uiState.highlighterColor, width, CanvasTool.HIGHLIGHTER) },
            onShapeChange = { shape -> viewModel.setMarkerShape(shape) }
        )
    }

    if (selectedPageForTemplate != null) {
        TemplateSelectionDialog(
            onDismiss = { selectedPageForTemplate = null },
            onSelect = { background ->
                viewModel.updatePageBackground(selectedPageForTemplate!!, background)
                selectedPageForTemplate = null
            }
        )
    }
}

@Composable
private fun ToolRow(
    activeTool: CanvasTool,
    onPenClick: () -> Unit,
    onHighlighterClick: () -> Unit,
    onEraserClick: () -> Unit,
    onLassoClick: () -> Unit,
    onImageClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onPasteClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteSelectionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolIcon(Icons.Rounded.Edit, activeTool == CanvasTool.PEN, onPenClick)
        ToolIcon(Icons.Rounded.Brush, activeTool == CanvasTool.HIGHLIGHTER, onHighlighterClick)
        ToolIcon(Icons.Rounded.AutoFixHigh, activeTool == CanvasTool.ERASER, onEraserClick)
        ToolIcon(Icons.Rounded.Gesture, activeTool == CanvasTool.LASSO, onLassoClick)
        ToolIcon(Icons.Rounded.Image, activeTool == CanvasTool.IMAGE, onImageClick)
        ToolIcon(Icons.Rounded.Undo, false, onUndoClick)
        ToolIcon(Icons.Rounded.Redo, false, onRedoClick)
        ToolIcon(Icons.Rounded.ContentCopy, false, onCopyClick)
        ToolIcon(Icons.Rounded.ContentPaste, false, onPasteClick)
        ToolIcon(Icons.Rounded.Delete, false, onDeleteSelectionClick)
        ToolIcon(Icons.Rounded.Tune, false, {})
    }
}

@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .background(
                if (active) Color(0xFFEDE7F6) else Color.Transparent,
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color(0xFF6C5CE7) else Color(0xFF5F5A74)
        )
    }
}

@Composable
private fun SinglePageCanvas(
    page: PageUiModel,
    uiState: CanvasUiState,
    viewModel: CanvasViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.707f)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .background(Color.White, RoundedCornerShape(10.dp))
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

        page.images.forEach { image ->
            val selected = uiState.selectedImages.any { it.id == image.id }
            if (!selected) {
                DraggableImage(
                    image = image,
                    enabled = uiState.activeTool == CanvasTool.IMAGE
                ) { newX, newY, newW, newH ->
                    viewModel.updateImageBounds(page.page.id, image.id, newX, newY, newW, newH)
                }
            }
        }
    }
}

@Composable
private fun PenSettingsDialog(
    currentColor: Int,
    currentWidth: Float,
    currentPenType: PenType,
    onDismiss: () -> Unit,
    onColorChange: (Int) -> Unit,
    onWidthChange: (Float) -> Unit,
    onPenTypeChange: (PenType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Pen settings") },
        text = {
            Column {
                Text("Pen type", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentPenType == PenType.BALLPOINT,
                        onClick = { onPenTypeChange(PenType.BALLPOINT) },
                        label = { Text("Ball") }
                    )
                    FilterChip(
                        selected = currentPenType == PenType.FOUNTAIN,
                        onClick = { onPenTypeChange(PenType.FOUNTAIN) },
                        label = { Text("Fountain") }
                    )
                    FilterChip(
                        selected = currentPenType == PenType.CALLIGRAPHY,
                        onClick = { onPenTypeChange(PenType.CALLIGRAPHY) },
                        label = { Text("Calligraphy") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Width: ${currentWidth.toInt()} px", fontSize = 14.sp)
                Slider(
                    value = currentWidth,
                    onValueChange = onWidthChange,
                    valueRange = 1f..24f
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Color", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorRow(
                    palette = PenPalette,
                    selectedColor = currentColor,
                    onColorSelected = { onColorChange(it) }
                )
            }
        }
    )
}

@Composable
private fun HighlighterSettingsDialog(
    currentColor: Int,
    currentWidth: Float,
    currentShape: MarkerShape,
    onDismiss: () -> Unit,
    onColorChange: (Int) -> Unit,
    onWidthChange: (Float) -> Unit,
    onShapeChange: (MarkerShape) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Highlighter settings") },
        text = {
            Column {
                Text("Shape", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentShape == MarkerShape.ROUND,
                        onClick = { onShapeChange(MarkerShape.ROUND) },
                        label = { Text("Round") }
                    )
                    FilterChip(
                        selected = currentShape == MarkerShape.SQUARE,
                        onClick = { onShapeChange(MarkerShape.SQUARE) },
                        label = { Text("Square") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Width: ${currentWidth.toInt()} px", fontSize = 14.sp)
                Slider(
                    value = currentWidth,
                    onValueChange = onWidthChange,
                    valueRange = 8f..48f
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Color", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorRow(
                    palette = HighlighterPalette,
                    selectedColor = currentColor,
                    onColorSelected = { onColorChange(it) }
                )
            }
        }
    )
}

@Composable
private fun ColorRow(
    palette: List<Color>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.forEach { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (selectedColor == color.toArgb()) 3.dp else 1.dp,
                        color = if (selectedColor == color.toArgb()) Color(0xFF6C5CE7) else Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color.toArgb()) }
            )
        }
    }
}

@Composable
private fun TemplateSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (PageBackground) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Choose page template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateButton("Plain") { onSelect(PageBackground.PLAIN) }
                TemplateButton("Ruled") { onSelect(PageBackground.RULED) }
                TemplateButton("Grid") { onSelect(PageBackground.GRID) }
                TemplateButton("Dots") { onSelect(PageBackground.DOT_GRID) }
                TemplateButton("Music") { onSelect(PageBackground.MUSIC_LINES) }
            }
        }
    )
}

@Composable
private fun TemplateButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8F5FF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun DraggableImage(
    image: CanvasImage,
    enabled: Boolean,
    onUpdate: (Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    var x by remember(image.id) { mutableFloatStateOf(image.x) }
    var y by remember(image.id) { mutableFloatStateOf(image.y) }
    var width by remember(image.id) { mutableFloatStateOf(image.width) }
    var height by remember(image.id) { mutableFloatStateOf(image.height) }

    LaunchedEffect(image.uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(image.uri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    withContext(Dispatchers.Main) {
                        bitmap = bmp?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
    ) {
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFF6C5CE7), RoundedCornerShape(6.dp))
                )
            }
        }
    }

    if (enabled) {
        LaunchedEffect(x, y, width, height) {
            onUpdate(x, y, width, height)
        }
    }
}

@Composable
private fun BackgroundCanvas(backgroundType: PageBackground) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val lineColor = Color(0xFFB0B7C3)
        val spacing = 40f

        when (backgroundType) {
            PageBackground.PLAIN -> Unit

            PageBackground.RULED -> {
                var y = spacing
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += spacing
                }
            }

            PageBackground.GRID -> {
                var y = spacing
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += spacing
                }

                var x = spacing
                while (x < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += spacing
                }
            }

            PageBackground.DOT_GRID -> {
                var y = spacing
                while (y < size.height) {
                    var x = spacing
                    while (x < size.width) {
                        drawCircle(
                            color = lineColor.copy(alpha = 0.5f),
                            radius = 2f,
                            center = Offset(x, y)
                        )
                        x += spacing
                    }
                    y += spacing
                }
            }

            PageBackground.MUSIC_LINES -> {
                val staffGap = 14f
                var startY = spacing
                while (startY + staffGap * 4 < size.height) {
                    repeat(5) { i ->
                        val y = startY + i * staffGap
                        drawLine(
                            color = lineColor.copy(alpha = 0.45f),
                            start = Offset(20f, y),
                            end = Offset(size.width - 20f, y),
                            strokeWidth = 1f
                        )
                    }
                    startY += 90f
                }
            }
        }
    }
}