package com.example.notebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.notebook.ui.theme.*

@Composable
fun Sidebar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(PastelSidebar)
            .padding(24.dp)
    ) {
        Text("PastelNote", style = MaterialTheme.typography.headlineMedium, color = ToolbarIconActive)
        Spacer(Modifier.height(32.dp))

        // כפתור בית
        SidebarItem("Home", Icons.Rounded.Home, currentRoute == "home") { onNavigate("home") }
    }
}

@Composable
fun SidebarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PastelSidebarSelected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) ToolbarIconActive else ToolbarIcon)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (isSelected) ToolbarIconActive else PastelOnBackground)
    }
}