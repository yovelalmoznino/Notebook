package com.example.notebook.ui.screen.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notebook.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    notebookId: Long, // נשמר למקרה שנצטרך אותו בעתיד, ה-ViewModel משתמש בו פנימית
    onBack: () -> Unit,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.notebookTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "חזרה")
                    }
                },
                actions = {
                    // כפתור החלפה בין עט למחק
                    IconButton(onClick = {
                        val nextTool = if (uiState.activeTool == CanvasTool.PEN)
                            CanvasTool.ERASER else CanvasTool.PEN
                        viewModel.setActiveTool(nextTool)
                    }) {
                        Icon(
                            imageVector = if (uiState.activeTool == CanvasTool.PEN)
                                Icons.Rounded.Edit else Icons.Rounded.AutoFixHigh,
                            tint = if (uiState.activeTool == CanvasTool.ERASER)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            contentDescription = "החלף כלי"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        DrawingCanvas(
            strokes = uiState.strokes,
            currentStroke = uiState.currentStroke,
            onAction = { event -> viewModel.handleMotionEvent(event) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        )
    }
}