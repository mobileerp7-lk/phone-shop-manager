package app.phoneshop.manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    repo: Repository,
    settings: ShopSettings,
    onAddProduct: () -> Unit,
    onGoStock: () -> Unit,
    onNewSale: () -> Unit,
    onNewRepair: () -> Unit,
    onReports: () -> Unit,
) {
    val sales by repo.sales.collectAsState(initial = emptyList())
    val lines by repo.allSaleLines.collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val types by repo.entryTypes.collectAsState(initial = emptyList())
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val returns by repo.saleReturns.collectAsState(initial = emptyList())
    val adjustments by repo.adjustments.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val day = ReportMath.build(ReportMath.startOf(today), ReportMath.startOf(today.plusDays(1)), sales, lines, entries, types.associate { it.id to it.name }, returns, adjustments)
    val products by repo.products.collectAsState(initial = emptyList())
    val stock by repo.stock.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)

    val itemsInStock = stock.values.sumOf { it.qty }
    val stockValue = stock.values.sumOf { it.totalCost }
    val lowStock = products.count { p -> (stock[p.id]?.qty ?: 0) <= p.lowStockLevel }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM"))

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.riseIn(0)) {
            Text(
                settings.shopName.ifBlank { "Your shop" },
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
            )
            Text("$date · amounts in ${currency.code}", color = Muted, fontSize = 14.sp)
        }

        SectionLabel("Today")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Sales today", Money.format(day.revenue, currency), Modifier.weight(1f).riseIn(1))
            if (Session.canSeeCost) StatCard("Profit today", Money.format(day.netProfit, currency), Modifier.weight(1f).riseIn(2), accent = if (day.netProfit >= 0) Good else Bad)
            else StatCard("Bills", day.bills.toString(), Modifier.weight(1f).riseIn(2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Bills", day.bills.toString(), Modifier.weight(1f).riseIn(3))
            if (Session.canSeeMoney) StatCard("All shop money", Money.format(views.sumOf { it.balance }, currency), Modifier.weight(1f).riseIn(4))
            else StatCard("Low stock", lowStock.toString(), Modifier.weight(1f).riseIn(4), accent = if (lowStock > 0) Warn else Good)
        }
        SectionLabel("Stock snapshot")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Products", products.size.toString(), Modifier.weight(1f).riseIn(1))
            StatCard("Items in stock", itemsInStock.toString(), Modifier.weight(1f).riseIn(2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Session.canSeeCost) StatCard("Stock value at cost", Money.format(stockValue, currency), Modifier.weight(1f).riseIn(3))
            else StatCard("Items in stock", itemsInStock.toString(), Modifier.weight(1f).riseIn(3))
            StatCard(
                "Low stock",
                lowStock.toString(),
                Modifier.weight(1f).riseIn(4),
                accent = if (lowStock > 0) Warn else Good,
            )
        }

        SectionLabel("Quick actions")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Add product", true, Modifier.weight(1f).riseIn(5), onAddProduct)
            ActionTile("View stock", true, Modifier.weight(1f).riseIn(6), onGoStock)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("New sale", true, Modifier.weight(1f).riseIn(7), onNewSale)
            ActionTile("New repair", true, Modifier.weight(1f).riseIn(8), onNewRepair)
        }
        if (Session.canSeeMoney) ActionTile("Reports", true, Modifier.fillMaxWidth().riseIn(8), onReports)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, accent: androidx.compose.ui.graphics.Color = Ink) {
    GlassCard(modifier) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

@Composable
private fun ActionTile(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(
        modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontWeight = FontWeight.SemiBold, color = if (enabled) Indigo else Muted)
                if (!enabled) Text("Soon", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
