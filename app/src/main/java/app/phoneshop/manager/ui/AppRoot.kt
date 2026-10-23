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
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    // The chosen colour theme applies to the whole app and to the phone's status bar icons.
    val theme = Themes.byId(settings?.themeId.orEmpty())
    SideEffect { Themes.current = theme }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !theme.dark
            controller.isAppearanceLightNavigationBars = !theme.dark
        }
    }
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
                onNavigate = { route -> nav.navigate(route) },
            )
        }
        composable("settings") {
            SettingsScreen(repo, settings, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
        }
        composable("entry_types") { EntryTypesScreen(repo, onBack = { nav.popBackStack() }) }
        composable(
            "money_entry/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            MoneyEntryScreen(repo, settings, entry.arguments?.getString("kind") ?: "EXPENSE", onBack = { nav.popBackStack() })
        }
        composable(
            "statement/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            StatementScreen(repo, settings, entry.arguments?.getString("id") ?: "ALL", onBack = { nav.popBackStack() })
        }
        composable("services") { ServicesScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("categories") { CategoriesScreen(repo, onBack = { nav.popBackStack() }) }
        composable(
            "contacts/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            ContactsScreen(
                repo, settings,
                startKind = entry.arguments?.getString("kind") ?: CUSTOMER,
                onBack = { nav.popBackStack() },
                onAdd = { kind -> nav.navigate("contact_form/$kind") },
                onOpen = { id -> nav.navigate("contact/$id") },
            )
        }
        composable(
            "contact_form/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            ContactFormScreen(repo, settings, entry.arguments?.getString("kind") ?: CUSTOMER, null, onBack = { nav.popBackStack() })
        }
        composable(
            "contact_edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ContactFormScreen(repo, settings, CUSTOMER, entry.arguments?.getString("id"), onBack = { nav.popBackStack() })
        }
        composable(
            "contact/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            ContactDetailScreen(repo, settings, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("contact_edit/$id") })
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
