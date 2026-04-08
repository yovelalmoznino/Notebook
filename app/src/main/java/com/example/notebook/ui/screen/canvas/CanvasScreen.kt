package com.example.notebook.ui.screen.canvas

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue // חשוב מאוד עבור 'by'
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    notebookId: Long,
    onBack: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(notebookId) {
        viewModel.loadNotebook(notebookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.notebookTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nextTool = if (uiState.activeTool == DrawingTool.PEN) DrawingTool.ERASER else DrawingTool.PEN
                        viewModel.setTool(nextTool)
                    }) {
                        Icon(
                            imageVector = if (uiState.activeTool == DrawingTool.PEN) Icons.Rounded.Edit else Icons.Rounded.AutoFixHigh,
                            tint = if (uiState.activeTool == DrawingTool.ERASER) PastelPrimary else ToolbarIcon,
                            contentDescription = "Toggle Tool"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PastelSurface)
            )
        }
    ) { padding ->
        DrawingCanvas(
            strokes = uiState.strokes,
            currentStroke = uiState.currentStroke,
            onAction = { event -> viewModel.handleMotionEvent(event) },
            modifier = Modifier.padding(padding).fillMaxSize()
        )
    }
}