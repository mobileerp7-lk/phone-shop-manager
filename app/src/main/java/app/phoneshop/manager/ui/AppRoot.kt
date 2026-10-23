package app.phoneshop.manager.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings

@Composable
fun AppRoot(repo: Repository) {
    val loaded by repo.settings.collectAsState(initial = null)
    val settings = loaded
    GradientBackground {
        if (settings == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Phone Shop Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Indigo)
            }
        } else {
            AppNav(repo, settings)
        }
    }
}

@Composable
private fun AppNav(repo: Repository, settings: ShopSettings) {
    val nav = rememberNavController()
    val reduce = LocalReduceMotion.current
    val enter: () -> EnterTransition = {
        if (reduce) EnterTransition.None else fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 8 }
    }
    val exit: () -> ExitTransition = {
        if (reduce) ExitTransition.None else fadeOut(tween(200))
    }
    val popEnter: () -> EnterTransition = {
        if (reduce) EnterTransition.None else fadeIn(tween(300))
    }
    val popExit: () -> ExitTransition = {
        if (reduce) ExitTransition.None else fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 8 }
    }

    NavHost(
        navController = nav,
        startDestination = if (settings.setupDone) "main" else "setup",
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable("setup") {
            SetupScreen(repo, settings) {
                nav.navigate("main") { popUpTo("setup") { inclusive = true } }
            }
        }
        composable("main") {
            MainShell(
                repo = repo,
                settings = settings,
                onAddProduct = { nav.navigate("product_form") },
                onOpenProduct = { id -> nav.navigate("product/$id") },
            )
        }
        composable("product_form") {
            ProductFormScreen(repo, settings, productId = null, onBack = { nav.popBackStack() })
        }
        composable(
            "product_edit/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            ProductFormScreen(
                repo, settings,
                productId = entry.arguments?.getString("productId"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "product/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("productId") ?: ""
            ProductDetailScreen(
                repo, settings, id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("product_edit/$id") },
            )
        }
    }
}
