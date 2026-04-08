package com.example.notebook.ui.screen.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.notebookTitle, color = PastelOnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "חזרה", tint = ToolbarIcon)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nextTool = if (uiState.activeTool == CanvasTool.PEN)
                            CanvasTool.ERASER else CanvasTool.PEN
                        viewModel.setActiveTool(nextTool)
                    }) {
                        Icon(
                            imageVector = if (uiState.activeTool == CanvasTool.PEN)
                                Icons.Rounded.Edit else Icons.Rounded.AutoFixHigh,
                            tint = if (uiState.activeTool == CanvasTool.ERASER)
                                ToolbarIconActive else ToolbarIcon,
                            contentDescription = "החלף כלי"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PastelSurface)
            )
        },
        containerColor = PastelBackground // רקע המסך (מאחורי הדפים)
    ) { padding ->

        // כאן הקסם: רשימה נגללת של דפים
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // יצירת קנבס נפרד עבור כל דף במחברת
            items(uiState.pages, key = { it.page.id }) { pageModel ->
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(0.7f) // יחס גובה-רוחב שמזכיר דף A4
                        .shadow(4.dp) // הצללה שנותנת תחושה של דף נייר אמיתי
                        .background(Color.White) // צבע הדף עצמו
                ) {
                    DrawingCanvas(
                        strokes = pageModel.strokes,
                        // אנחנו מעבירים את הקו הנוכחי רק אם הוא שייך לדף הספציפי הזה
                        currentStroke = if (uiState.drawingPageId == pageModel.page.id) uiState.currentStroke else null,
                        onAction = { event -> viewModel.handleMotionEvent(pageModel.page.id, event) }
                    )
                }
            }

            // כפתור הוספת דף בסוף המחברת
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.addNewPage() },
                    modifier = Modifier.padding(bottom = 32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ToolbarIcon)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("הוסף עמוד למחברת")
                }
            }
        }
    }
}