package com.example.notebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.notebook.ui.screen.dashboard.DashboardScreen
import com.example.notebook.ui.screen.canvas.CanvasScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            DashboardScreen(
                onFolderClick = { id -> navController.navigate(Screen.FolderDetail.createRoute(id)) },
                onNotebookClick = { id -> navController.navigate(Screen.Canvas.createRoute(id)) }
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId")
            DashboardScreen(
                folderId = folderId,
                onFolderClick = { id -> navController.navigate(Screen.FolderDetail.createRoute(id)) },
                onNotebookClick = { id -> navController.navigate(Screen.Canvas.createRoute(id)) }
            )
        }

        composable(
            route = Screen.Canvas.route,
            arguments = listOf(navArgument("notebookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getLong("notebookId") ?: 0L
            CanvasScreen(notebookId = notebookId, onBack = { navController.popBackStack() })
        }
    }
}