package app.phoneshop.manager.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
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
    val staff by repo.staff.collectAsState(initial = null)
    val people = staff
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { Lang.current = app.phoneshop.manager.util.Prefs.language(context) }
    // A daily backup into the folder the owner picked, if more than a day has passed.
    androidx.compose.runtime.LaunchedEffect(settings?.backupTreeUri, settings?.setupDone) {
        val s = settings
        if (s != null && s.setupDone && s.backupTreeUri.isNotBlank() && System.currentTimeMillis() - s.lastBackupAt > 20L * 3600_000L) {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.phoneshop.manager.util.Backup.writeToFolder(context, repo, s.backupTreeUri) }
            if (ok) repo.saveSettings(repo.currentSettings().copy(lastBackupAt = System.currentTimeMillis()))
        }
    }
    // Live sharing: every few seconds this phone sends its changes and fetches the other phones' changes.
    androidx.compose.runtime.LaunchedEffect(settings?.setupDone) {
        if (settings?.setupDone != true) return@LaunchedEffect
        while (true) {
            if (app.phoneshop.manager.util.Prefs.shopId(context).isNotBlank()) {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.phoneshop.manager.util.SyncEngine.syncOnce(context, repo)
                }
                app.phoneshop.manager.util.Prefs.setSyncStatus(
                    context,
                    if (result == null) "Last shared " + java.time.LocalTime.now().withNano(0) else "Waiting: $result",
                )
            }
            kotlinx.coroutines.delay(20_000L)
        }
    }
    val needsLogin = people != null && settings != null && settings.setupDone &&
        (people.size > 1 || people.any { it.pinHash.isNotEmpty() })
    // With one person and no PIN the app opens straight away as the owner.
    androidx.compose.runtime.LaunchedEffect(people, needsLogin) {
        if (people != null && !needsLogin && Session.staff == null) {
            Session.staff = people.firstOrNull()
            repo.actorName = people.firstOrNull()?.name ?: "Owner"
        }
    }
    GradientBackground {
        if (settings == null || people == null || (Session.staff == null && !needsLogin && settings.setupDone)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Phone Shop Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Indigo)
            }
        } else if (needsLogin && Session.staff == null) {
            LoginScreen(repo, people) { s -> Session.staff = s; repo.actorName = s.name }
        } else {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { AppNav(repo, settings) }
            }
        }
    }
}

@Composable
private fun AppNav(repo: Repository, settings: ShopSettings) {
    val nav = rememberNavController()
    val cart = remember { CartState() }
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
                cart = cart,
                onPay = { nav.navigate("payment") },
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
        composable("payment") {
            PaymentScreen(
                repo, settings, cart,
                onBack = { nav.popBackStack() },
                onDone = { saleId -> nav.navigate("bill/$saleId") { popUpTo("payment") { inclusive = true } } },
            )
        }
        composable(
            "bill/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            BillScreen(
                repo, settings, entry.arguments?.getString("id") ?: "",
                onBack = { nav.popBackStack() },
                onNewSale = { cart.requestTab = 1; nav.popBackStack("main", false) },
                onReturn = { nav.navigate("return/${entry.arguments?.getString("id") ?: ""}") },
            )
        }
        composable("bills") {
            BillsListScreen(repo, settings, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("bill/$it") }, onTradeIn = { nav.navigate("tradein") })
        }
        composable("tradein") { TradeInScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("adjust") { StockAdjustScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("quotes") {
            QuotesScreen(repo, settings, cart, onBack = { nav.popBackStack() }, onOpenCart = { cart.requestTab = 1; nav.popBackStack("main", false) })
        }
        composable(
            "return/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ReturnScreen(repo, settings, entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
        composable(
            "settle/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            SettleScreen(repo, settings, entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
        composable(
            "purchases/{tab}",
            arguments = listOf(navArgument("tab") { type = NavType.StringType }),
        ) { entry ->
            PurchasesScreen(
                repo, settings, entry.arguments?.getString("tab") ?: "ORDERS",
                onBack = { nav.popBackStack() },
                onNewOrder = { nav.navigate("po_form/new") },
                onOpenOrder = { nav.navigate("po/$it") },
                onReceiveNew = { nav.navigate("receive/new") },
            )
        }
        composable(
            "po_form/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: "new"
            PoFormScreen(
                repo, settings, if (id == "new") null else id,
                onBack = { nav.popBackStack() },
                onSaved = { saved -> nav.popBackStack(); if (id == "new") nav.navigate("po/$saved") },
            )
        }
        composable(
            "po/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            PoDetailScreen(repo, settings, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("po_form/$id") }, onReceive = { nav.navigate("receive/$id") })
        }
        composable(
            "receive/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: "new"
            ReceiveScreen(repo, settings, if (id == "new") null else id, onBack = { nav.popBackStack() })
        }
        composable("repairs") {
            RepairsScreen(repo, settings, onBack = { nav.popBackStack() }, onNew = { nav.navigate("job_form/new") }, onOpen = { nav.navigate("job/$it") })
        }
        composable(
            "job_form/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: "new"
            JobFormScreen(
                repo, settings, if (id == "new") null else id,
                onBack = { nav.popBackStack() },
                onSaved = { saved -> nav.popBackStack(); if (id == "new") nav.navigate("job/$saved") },
            )
        }
        composable(
            "job/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            JobDetailScreen(repo, settings, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("job_form/$id") }, onOpenBill = { nav.navigate("bill/$it") })
        }
        composable("reports") { ReportsHubScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }) }
        composable("report_daily") { DailyScreen(repo, settings, onBack = { nav.popBackStack() }, onDayClose = { nav.navigate("dayclose") }) }
        composable("report_monthly") { MonthlyScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("report_pl") { ProfitLossScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("dayclose") { DayCloseScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("printing") { PrintingScreen(onBack = { nav.popBackStack() }) }
        composable("cloud") { CloudScreen(repo, settings, onBack = { nav.popBackStack() }) }
        composable("staff") { StaffScreen(repo, onBack = { nav.popBackStack() }) }
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
            ContactDetailScreen(
                repo, settings, id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("contact_edit/$id") },
                onCollect = { nav.navigate("settle/$id") },
                onSell = {
                    cart.customerId = id
                    cart.requestTab = 1
                    nav.popBackStack("main", false)
                },
                onOpenBill = { billId -> nav.navigate("bill/$billId") },
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
