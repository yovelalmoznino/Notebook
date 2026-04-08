package com.example.notebook.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")

    object FolderDetail : Screen("folder/{folderId}") {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }

    object Canvas : Screen("canvas/{notebookId}") {
        fun createRoute(notebookId: Long) = "canvas/$notebookId"
    }
}