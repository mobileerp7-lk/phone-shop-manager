package app.phoneshop.manager.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** One place that gathers everything a report needs. */
private class Inputs(
    val sales: List<app.phoneshop.manager.data.Sale>,
    val lines: List<app.phoneshop.manager.data.SaleLine>,
    val entries: List<app.phoneshop.manager.data.MoneyEntry>,
    val types: Map<String, String>,
) {
    fun report(from: LocalDate, toExclusive: LocalDate) =
        ReportMath.build(ReportMath.startOf(from), ReportMath.startOf(toExclusive), sales, lines, entries, types)
}

@Composable
private fun rememberInputs(repo: Repository): Inputs {
    val sales by repo.sales.collectAsState(initial = emptyList())
    val lines by repo.allSaleLines.collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val types by repo.entryTypes.collectAsState(initial = emptyList())
    return Inputs(sales, lines, entries, types.associate { it.id to it.name })
}

private fun signedMinus(v: Long, c: CurrencyInfo) = "− " + Money.format(v, c)

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, muted: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = if (muted) Muted else Ink, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

/** More → Reports. */
@Composable
fun ReportsHubScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    ScreenScaffold("Reports", onBack) {
        NavRow("Daily summary", "", { onNavigate("report_daily") })
        NavRow("Monthly report", "", { onNavigate("report_monthly") })
        NavRow("Profit and loss", "", { onNavigate("report_pl") })
        NavRow("Day close (count the cash)", "", { onNavigate("dayclose") })
    }
}

/** How the business did on one day, with money movement and credit. */
@Composable
fun DailyScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit, onDayClose: () -> Unit) {
    val inputs = rememberInputs(repo)
    val accounts by repo.accounts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val context = LocalContext.current
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(day)
    val r = inputs.report(date, date.plusDays(1))
    val start = ReportMath.startOf(date)
    val end = ReportMath.startOf(date.plusDays(1))

    ScreenScaffold("Daily summary", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { day = date.minusDays(1).toString() }) { Text("◀ Previous") }
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { day = date.plusDays(1).toString() }, enabled = date < LocalDate.now()) { Text("Next ▶") }
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Line("Product sales (${r.bills} bills)", Money.format(r.productSales, currency))
            Line("Repairs delivered (${r.repairJobs} jobs)", Money.format(r.repairIncome, currency))
            if (r.serviceIncome > 0) Line("Services (${r.serviceCount})", Money.format(r.serviceIncome, currency))
            Line("Other income", Money.format(r.otherIncome, currency))
            Line("Cost of items sold", signedMinus(r.productCost, currency), muted = true)
            Line("Repair parts used", signedMinus(r.repairCost + r.serviceCost, currency), muted = true)
            if (r.discounts > 0) Line("Discounts given", signedMinus(r.discounts, currency), muted = true)
            Line("Expenses (${r.expenseCount})", signedMinus(r.expenses, currency), muted = true)
            Line("Profit today", Money.format(r.netProfit, currency), bold = true)
        }
        SectionLabel("Money movement")
        GlassCard(Modifier.fillMaxWidth()) {
            accounts.forEach { a ->
                val mine = inputs.entries.filter { it.accountId == a.id && it.voidedAt == null }
                val opening = a.openingBalance + mine.filter { it.entryDate < start }.sumOf { it.amount }
                val closing = a.openingBalance + mine.filter { it.entryDate < end }.sumOf { it.amount }
                Line(a.name, "${Money.format(opening, currency)} → ${Money.format(closing, currency)}")
            }
        }
        SectionLabel("Credit")
        GlassCard(Modifier.fillMaxWidth()) {
            Line("New customer debt", Money.format(r.newDebt, currency))
            Line("Debt collected", Money.format(r.debtCollected, currency))
            Line("Paid to suppliers", Money.format(r.supplierPaid, currency))
        }
        if (r.topItems.isNotEmpty()) {
            SectionLabel("Top items")
            GlassCard(Modifier.fillMaxWidth()) {
                r.topItems.forEach { (name, qty, _) -> Line(name, "$qty sold") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Share summary", {
                val text = "${settings.shopName} · ${date}\nSales ${Money.format(r.revenue, currency)}\nProfit ${Money.format(r.netProfit, currency)}\nExpenses ${Money.format(r.expenses, currency)}"
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.weight(1f))
            GradientButton("Close the day", onDayClose, Modifier.weight(1f))
        }
    }
}

/** A month at a glance, with a bar for each day's sales. */
@Composable
fun MonthlyScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val inputs = rememberInputs(repo)
    val products by repo.products.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val first = month.atDay(1)
    val next = month.plusMonths(1).atDay(1)
    val r = inputs.report(first, next)
    val prev = inputs.report(month.minusMonths(1).atDay(1), first)

    val daily = (1..month.lengthOfMonth()).map { d ->
        val day = month.atDay(d)
        inputs.report(day, day.plusDays(1)).revenue
    }
    val maxDay = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barColor = Indigo
    val categoryByProduct = products.associate { it.id to (categories.firstOrNull { c -> c.id == it.categoryId }?.name ?: "Other") }
    val monthSaleIds = inputs.sales.filter { it.status == "DONE" && it.createdAt >= ReportMath.startOf(first) && it.createdAt < ReportMath.startOf(next) }.map { it.id }.toSet()
    val mix = inputs.lines.filter { it.saleId in monthSaleIds }.groupBy {
        when {
            it.kind == "PRODUCT" -> categoryByProduct[it.productId] ?: "Products"
            it.name.startsWith("Repair #") -> "Repairs"
            else -> "Services"
        }
    }.map { it.key to it.value.sumOf { l -> l.lineTotal } }.sortedByDescending { it.second }
    val mixTotal = mix.sumOf { it.second }.coerceAtLeast(1L)
    val change = if (prev.revenue > 0) ((r.revenue - prev.revenue) * 100 / prev.revenue) else null

    ScreenScaffold("Monthly report", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("◀") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.now()) { Text("▶") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard(Modifier.weight(1f)) {
                Text("Sales", color = Muted, fontSize = 12.sp)
                Text(Money.format(r.revenue, currency), fontWeight = FontWeight.ExtraBold)
                if (change != null) Text((if (change >= 0) "+" else "") + "$change% vs last month", color = if (change >= 0) Good else Bad, fontSize = 11.sp)
            }
            GlassCard(Modifier.weight(1f)) {
                Text("Repairs", color = Muted, fontSize = 12.sp)
                Text(Money.format(r.repairIncome, currency), fontWeight = FontWeight.ExtraBold)
                Text("${r.repairJobs} jobs", color = Muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard(Modifier.weight(1f)) {
                Text("Expenses", color = Muted, fontSize = 12.sp)
                Text(Money.format(r.expenses, currency), fontWeight = FontWeight.ExtraBold)
            }
            GlassCard(Modifier.weight(1f)) {
                Text("Net profit", color = Muted, fontSize = 12.sp)
                Text(Money.format(r.netProfit, currency), fontWeight = FontWeight.ExtraBold, color = if (r.netProfit >= 0) Good else Bad)
                Text("owner only", color = Muted, fontSize = 11.sp)
            }
        }
        SectionLabel("Sales each day")
        GlassCard(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val n = daily.size
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                daily.forEachIndexed { i, v ->
                    val h = size.height * (v.toFloat() / maxDay)
                    drawRoundRect(
                        color = if (v == 0L) barColor.copy(alpha = 0.15f) else barColor,
                        topLeft = Offset(i * (w + gap), size.height - maxOf(h, 3.dp.toPx())),
                        size = Size(w, maxOf(h, 3.dp.toPx())),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Row { Text("1", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("${month.lengthOfMonth()}", color = Muted, fontSize = 11.sp) }
        }
        if (mix.isNotEmpty()) {
            SectionLabel("Where income came from")
            GlassCard(Modifier.fillMaxWidth()) {
                mix.forEach { (name, v) -> Line(name, "${v * 100 / mixTotal}%") }
            }
        }
        if (r.topItems.isNotEmpty()) {
            SectionLabel("Best sellers")
            GlassCard(Modifier.fillMaxWidth()) {
                r.topItems.forEachIndexed { i, (name, qty, profit) -> Line("${i + 1}. $name", "$qty sold · profit ${Money.format(profit, currency)}") }
            }
        }
    }
}

/** Profit and loss for today, this month, this year or any dates. */
@Composable
fun ProfitLossScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val inputs = rememberInputs(repo)
    val currency = Locales.currency(settings.currencyCode)
    var mode by rememberSaveable { mutableStateOf("MONTH") }
    var fromText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var toText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val today = LocalDate.now()
    val range: Pair<LocalDate, LocalDate> = when (mode) {
        "TODAY" -> today to today.plusDays(1)
        "YEAR" -> today.withDayOfYear(1) to today.plusDays(1)
        "DATES" -> (runCatching { LocalDate.parse(fromText) }.getOrDefault(today) to runCatching { LocalDate.parse(toText).plusDays(1) }.getOrDefault(today.plusDays(1)))
        else -> today.withDayOfMonth(1) to today.plusDays(1)
    }
    val r = inputs.report(range.first, range.second)
    ScreenScaffold("Profit and loss", onBack) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("TODAY" to "Today", "MONTH" to "This month", "YEAR" to "This year", "DATES" to "Pick dates")) { (k, label) ->
                GlassChip(label, mode == k) { mode = k }
            }
        }
        if (mode == "DATES") {
            GlassField(fromText, { fromText = it }, "From (YYYY-MM-DD)")
            GlassField(toText, { toText = it }, "To (YYYY-MM-DD)")
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Line("Product sales", Money.format(r.productSales, currency))
            Line("minus buying cost of items sold", signedMinus(r.productCost, currency), muted = true)
            Line("Profit on products", Money.format(r.productProfit, currency), bold = true)
            Line("Repair income", Money.format(r.repairIncome, currency))
            Line("minus spare parts used", signedMinus(r.repairCost, currency), muted = true)
            Line("Profit on repairs", Money.format(r.repairProfit, currency), bold = true)
            if (r.serviceIncome > 0) Line("Profit on other services", Money.format(r.serviceProfit, currency), bold = true)
            if (r.discounts > 0) Line("Discounts given", signedMinus(r.discounts, currency), muted = true)
            if (r.otherIncome > 0) Line("Other income", Money.format(r.otherIncome, currency))
            Line("Shop expenses", signedMinus(r.expenses, currency))
            r.expenseByType.forEach { (name, v) -> Line("   $name", Money.format(v, currency), muted = true) }
            Line("Net profit", Money.format(r.netProfit, currency), bold = true)
        }
        Text(
            "Not counted here, on purpose: transfers between accounts, money the owner put in or took out, old debts collected, and supplier bills paid for stock. These move money but do not create profit or loss.",
            color = Muted, fontSize = 13.sp,
        )
    }
}

/** Count the cash at the end of the day and lock the day. */
@Composable
fun DayCloseScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val closes by repo.dayCloses.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val start = ReportMath.startOf(today)
    val cash = views.filter { it.account.type == "CASH" }
    val cashIds = cash.map { it.account.id }.toSet()
    val todays = entries.filter { it.voidedAt == null && it.accountId in cashIds && it.entryDate >= start }
    val cashIn = todays.filter { it.amount > 0 }.sumOf { it.amount }
    val cashOut = -todays.filter { it.amount < 0 }.sumOf { it.amount }
    val expected = cash.sumOf { it.balance }
    val opening = expected - cashIn + cashOut
    val closed = closes.firstOrNull { it.id == today.toString() }

    var counted by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var left by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var moved by remember { mutableStateOf(false) }
    val countedMinor = Money.parse(counted)
    val diff = if (countedMinor == null) 0L else countedMinor - expected
    val leftMinor = Money.parse(left) ?: 0L
    val rest = ((countedMinor ?: 0L) - leftMinor).coerceAtLeast(0)
    val bank = views.firstOrNull { it.account.type == "BANK" }

    ScreenScaffold("Day close", onBack) {
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Line("Cash at opening", Money.format(opening, currency))
            Line("Cash in today", "+ " + Money.format(cashIn, currency))
            Line("Cash out today", "− " + Money.format(cashOut, currency))
            Line("App expects in drawer", Money.format(expected, currency), bold = true)
        }
        if (closed != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Day closed", color = Good, fontWeight = FontWeight.Bold)
                Line("Counted", Money.format(closed.countedCash, currency))
                if (closed.difference != 0L) Line(if (closed.difference < 0) "Short by" else "Extra", Money.format(kotlin.math.abs(closed.difference), currency))
                if (closed.reason.isNotBlank()) Text("Reason: ${closed.reason}", color = Muted, fontSize = 13.sp)
                Line("Left in drawer", Money.format(closed.leftInDrawer, currency))
            }
            GlassButton("Reopen this day", { scope.launch { repo.reopenDay(today.toString()) } }, Modifier.fillMaxWidth())
            Text("Only the owner should reopen a closed day.", color = Muted, fontSize = 12.sp)
        } else {
            GlassField(counted, { counted = it }, "Count the cash and type the total (${currency.code})", keyboard = KeyboardType.Decimal)
            if (countedMinor != null && diff != 0L) {
                Text(if (diff < 0) "Short by ${Money.format(-diff, currency)}" else "Extra ${Money.format(diff, currency)}", color = Bad, fontWeight = FontWeight.Bold)
                GlassField(reason, { reason = it }, "Reason (required when short or extra)")
            }
            GlassField(left, { left = it }, "Leave in drawer for tomorrow (${currency.code})", keyboard = KeyboardType.Decimal)
            if (countedMinor != null && bank != null && rest > 0 && !moved) {
                Text("The rest (${Money.format(rest, currency)}) can be moved to ${bank.account.name} with one tap.", color = Muted, fontSize = 13.sp)
                GlassButton("Move ${Money.format(rest, currency)} to ${bank.account.name}", {
                    val from = cash.firstOrNull()?.account?.id
                    if (from != null) scope.launch { error = repo.recordTransfer(from, bank.account.id, rest, "Day close"); if (error == null) moved = true }
                }, Modifier.fillMaxWidth())
            }
            if (moved) Text("Moved to the bank.", color = Good, fontWeight = FontWeight.SemiBold)
            error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
            GradientButton("Close the day", {
                when {
                    countedMinor == null -> error = "Count the cash and type the total."
                    diff != 0L && reason.isBlank() -> error = "Please write the reason for the difference."
                    else -> scope.launch { repo.closeDay(today.toString(), expected, countedMinor, reason, leftMinor) }
                }
            }, Modifier.fillMaxWidth())
            Text("Once closed, the day is marked finished. The owner can reopen it.", color = Muted, fontSize = 12.sp)
        }
    }
}
