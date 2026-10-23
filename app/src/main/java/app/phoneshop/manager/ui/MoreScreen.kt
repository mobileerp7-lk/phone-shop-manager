package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class MoreItem(val label: String, val route: String)

private val sections = listOf(
    "Shop" to listOf(
        MoreItem("Sell (POS)", "sell"),
        MoreItem("Bills and returns", "bills"),
        MoreItem("Quotations", "quotes"),
        MoreItem("Repairs", "repairs"),
    ),
    "Stock" to listOf(
        MoreItem("Products", "products"),
        MoreItem("Purchase orders", "po"),
        MoreItem("Purchases and supplier bills", "purchases"),
        MoreItem("Stock adjustments", "adjust"),
    ),
    "People" to listOf(
        MoreItem("Customers", "contacts/CUSTOMER"),
        MoreItem("Suppliers", "contacts/SUPPLIER"),
    ),
    "Money" to listOf(
        MoreItem("Money accounts", "accounts"),
        MoreItem("Expenses and income", "money_entries"),
        MoreItem("Transfers", "transfers"),
    ),
    "Reports and admin" to listOf(
        MoreItem("Reports", "reports"),
        MoreItem("Day close", "dayclose"),
        MoreItem("Staff and activity log", "staff"),
        MoreItem("Settings", "settings"),
    ),
)

/** Routes that are built. Everything else shows dimmed with "Soon". */
object BuiltRoutes {
    val set = mutableSetOf(
        "products", "contacts/CUSTOMER", "contacts/SUPPLIER", "settings", "services",
        "cloud", "printing", "accounts", "money_entries", "transfers", "sell", "bills", "po", "purchases", "repairs", "reports", "dayclose", "quotes", "adjust", "staff",
    )
}

/** Cashiers do not see money, purchasing or settings; managers cannot open Settings. */
private fun allowed(route: String): Boolean = when (route) {
    "accounts", "money_entries", "transfers", "reports", "dayclose", "po", "purchases", "adjust", "staff", "settings" ->
        when (route) {
            "settings" -> Session.canManageShop
            "staff" -> Session.canSeeMoney
            else -> Session.canSeeMoney
        }
    else -> true
}

/** The "More" tab, laid out like the wireframe menu. */
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("More", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        var n = 0
        for ((title, all) in sections) {
            val items = all.filter { allowed(it.route) }
            if (items.isEmpty()) continue
            SectionLabel(title)
            for (item in items) {
                val ready = item.route in BuiltRoutes.set
                val index = minOf(n, 8)
                n += 1
                NavRow(
                    item.label,
                    value = if (ready) "" else "Soon",
                    onClick = if (ready) ({ onNavigate(item.route) }) else null,
                    modifier = Modifier.riseIn(index),
                )
            }
        }
    }
}
