package com.example.notebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.notebook.data.model.Notebook
import com.example.notebook.ui.theme.PastelOnBackground

@Composable
fun NotebookCard(
    notebook: Notebook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startColor = Color(android.graphics.Color.parseColor(notebook.coverColorStart))
    val endColor = Color(android.graphics.Color.parseColor(notebook.coverColorEnd))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f) // צורה של מחברת עומדת
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(startColor, endColor)))
                .padding(16.dp)
        ) {
            Text(
                text = notebook.title,
                style = MaterialTheme.typography.titleMedium,
                color = PastelOnBackground,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}