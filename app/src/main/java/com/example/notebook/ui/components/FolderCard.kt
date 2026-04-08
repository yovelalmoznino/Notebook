package com.example.notebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.notebook.data.model.Folder
import com.example.notebook.ui.theme.PastelOnBackground

@Composable
fun FolderCard(
    folder: Folder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // הפיכת מחרוזת הצבע מה-DB לאובייקט צבע אמיתי
    val folderColor = try {
        Color(android.graphics.Color.parseColor(folder.colorHex))
    } catch (e: Exception) {
        Color(0xFFFFD6E7) // צבע ברירת מחדל אם יש שגיאה
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = folderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = PastelOnBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = PastelOnBackground
            )
        }
    }
}