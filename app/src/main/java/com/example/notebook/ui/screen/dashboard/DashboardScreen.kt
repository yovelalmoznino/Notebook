package com.example.notebook.ui.screen.dashboard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.notebook.ui.components.FolderCard
import com.example.notebook.ui.components.NotebookCard
import com.example.notebook.ui.components.Sidebar
import com.example.notebook.ui.navigation.AppNavHost
import com.example.notebook.ui.theme.*

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Row(modifier = Modifier.fillMaxSize().background(PastelBackground)) {
        Sidebar(currentRoute = currentRoute) { route ->
            navController.navigate(route) { launchSingleTop = true }
        }

        // קו מפריד אסתטי
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(PastelDivider))

        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(navController = navController)
        }
    }
}

@Composable
fun DashboardScreen(
    folderId: Long? = null,
    onFolderClick: (Long) -> Unit,
    onNotebookClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            // כותרת וכפתורים
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (folderId == null) "Home" else "Folder Content",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )

                if (folderId != null) {
                    Button(
                        onClick = { viewModel.toggleNotebookDialog(true) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Create, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Notebook")
                    }
                }

                Button(
                    onClick = { viewModel.toggleFolderDialog(true) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Folder")
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PastelDivider))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PastelPrimary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.folders.isNotEmpty()) {
                        items(uiState.folders) { folder ->
                            FolderCard(folder = folder, onClick = { onFolderClick(folder.id) })
                        }
                    }
                    if (uiState.notebooks.isNotEmpty()) {
                        items(uiState.notebooks) { notebook ->
                            NotebookCard(notebook = notebook, onClick = { onNotebookClick(notebook.id) })
                        }
                    }
                    if (uiState.folders.isEmpty() && uiState.notebooks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateIndicator()
                        }
                    }
                }
            }
        }

        // חלונות קופצים (דיאלוגים)
        if (uiState.showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { viewModel.toggleFolderDialog(false) },
                onCreate = { name, color -> viewModel.createFolder(name, color) }
            )
        }

        if (uiState.showCreateNotebookDialog) {
            CreateNotebookDialog(
                onDismiss = { viewModel.toggleNotebookDialog(false) },
                onCreate = { title -> viewModel.createNotebook(title) }
            )
        }
    }
}

@Composable
private fun EmptyStateIndicator() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(64.dp), tint = PastelDivider)
        Spacer(Modifier.height(8.dp))
        Text("No items here yet", color = PastelDivider)
    }
}

// ---------------------------------------------------------
// רכיבי הדיאלוג
// ---------------------------------------------------------

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val colorsList = listOf(
        Color(0xFFFFD6E7), Color(0xFFFFECB3), Color(0xFFB5EAD7),
        Color(0xFFC7CEEA), Color(0xFFD4B8E0), Color(0xFFFFD7BA)
    )
    var selectedColor by remember { mutableStateOf(colorsList.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(100.dp)) {
                    items(colorsList) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (color == selectedColor) 2.dp else 0.dp, ToolbarIconActive, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val argb = android.graphics.Color.argb(
                        (selectedColor.alpha * 255).toInt(),
                        (selectedColor.red * 255).toInt(),
                        (selectedColor.green * 255).toInt(),
                        (selectedColor.blue * 255).toInt()
                    )
                    val hex = String.format("#%06X", 0xFFFFFF and argb)
                    onCreate(name, hex)
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CreateNotebookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Notebook") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Notebook Title") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title) },
                enabled = title.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}