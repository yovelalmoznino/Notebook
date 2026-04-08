package com.example.notebook.ui.screen.dashboard
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebook.data.model.Folder
import com.example.notebook.data.model.Notebook
import com.example.notebook.data.repository.FolderRepository
import com.example.notebook.data.repository.NotebookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val folders: List<Folder> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val isLoading: Boolean = true,
    val currentFolderId: Long? = null,
    val showCreateFolderDialog: Boolean = false,
    val showCreateNotebookDialog: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val folderRepo: FolderRepository,
    private val notebookRepo: NotebookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun loadFolder(folderId: Long?) {
        viewModelScope.launch {
            val folderFlow = if (folderId == null) folderRepo.observeRootFolders()
            else folderRepo.observeChildFolders(folderId)

            val notebookFlow = if (folderId != null) notebookRepo.observeNotebooksInFolder(folderId)
            else flowOf(emptyList())

            combine(folderFlow, notebookFlow) { f, n ->
                _uiState.value.copy(folders = f, notebooks = n, isLoading = false, currentFolderId = folderId)
            }.collect { _uiState.value = it }
        }
    }

    fun toggleFolderDialog(show: Boolean) { _uiState.update { it.copy(showCreateFolderDialog = show) } }
    fun toggleNotebookDialog(show: Boolean) { _uiState.update { it.copy(showCreateNotebookDialog = show) } }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            folderRepo.createFolder(name = name, parentId = _uiState.value.currentFolderId, colorHex = colorHex)
            toggleFolderDialog(false)
        }
    }

    fun createNotebook(title: String) {
        viewModelScope.launch {
            val folderId = _uiState.value.currentFolderId ?: return@launch
            notebookRepo.createNotebook(folderId, title)
            toggleNotebookDialog(false)
        }
    }
}