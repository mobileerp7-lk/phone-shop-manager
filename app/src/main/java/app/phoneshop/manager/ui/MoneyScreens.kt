package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Account
import app.phoneshop.manager.data.AccountView
import app.phoneshop.manager.data.MoneyEntry
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun signed(amount: Long, c: CurrencyInfo): String =
    (if (amount > 0) "+ " else if (amount < 0) "− " else "") + Money.format(kotlin.math.abs(amount), c)

private val accountTypes = listOf("Cash", "Bank", "Mobile wallet")
private val accountCodes = listOf("CASH", "BANK", "WALLET")

private fun kindLabel(kind: String) = when (kind) {
    "EXPENSE" -> "Expense"
    "INCOME" -> "Income"
    "TRANSFER" -> "Transfer"
    "OWNER_IN" -> "Owner put in"
    "OWNER_OUT" -> "Owner took out"
    "SALE" -> "Sale"
    "DEBT_PAID" -> "Debt collected"
    "SUPPLIER_PAID" -> "Paid supplier"
    "PURCHASE_PAID" -> "Paid for stock"
    "REPAIR_ADVANCE" -> "Repair advance"
    "RETURN_REFUND" -> "Refund"
    "TRADE_IN" -> "Trade-in"
    else -> kind
}

/** The Money tab: every account, quick actions and who owes what. */
@Composable
fun MoneyScreen(
    repo: Repository,
    settings: ShopSettings,
    onNavigate: (String) -> Unit,
) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    var addAccount by remember { mutableStateOf(false) }

    val total = views.sumOf { it.balance }
    val ownerNet = entries.filter { it.voidedAt == null }.sumOf { if (it.kind == "OWNER_IN" || it.kind == "OWNER_OUT") it.amount else 0L }
    val customersOwe = contacts.filter { it.kind == CUSTOMER }.sumOf { (balances[it.id] ?: 0L).coerceAtLeast(0) }
    val weOwe = contacts.filter { it.kind == SUPPLIER }.sumOf { (balances[it.id] ?: 0L).coerceAtLeast(0) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Money", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            TextButton(onClick = { addAccount = true }) { Text("+ Account", color = Indigo, fontWeight = FontWeight.Bold) }
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Text("All shop money", color = Muted, fontSize = 13.sp)
            Text(Money.format(total, currency), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Expense", "money out", Modifier.weight(1f).riseIn(1)) { onNavigate("money_entry/EXPENSE") }
            QuickTile("Income", "money in", Modifier.weight(1f).riseIn(2)) { onNavigate("money_entry/INCOME") }
            QuickTile("Transfer", "move money", Modifier.weight(1f).riseIn(3)) { onNavigate("money_entry/TRANSFER") }
        }
        SectionLabel("Accounts")
        views.forEachIndexed { i, v ->
            GlassCard(Modifier.fillMaxWidth().riseIn(minOf(i + 4, 8)), onClick = { onNavigate("statement/${v.account.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.account.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        val sub = buildString {
                            if (v.account.detail.isNotBlank()) append(v.account.detail).append(" · ")
                            append("Today + ${Money.format(v.todayIn, currency)} / − ${Money.format(v.todayOut, currency)}")
                        }
                        Text(sub, color = Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(v.balance, currency), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Owner's account", fontWeight = FontWeight.SemiBold)
                    Text("Money you put in or took out", color = Muted, fontSize = 12.sp)
                }
                Text(Money.format(ownerNet, currency), fontWeight = FontWeight.Bold)
            }
        }
        GlassButton("+ Owner puts in / takes out money", { onNavigate("money_entry/OWNER") }, Modifier.fillMaxWidth())
        SectionLabel("Who owes what")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard(Modifier.weight(1f), onClick = { onNavigate("contacts/CUSTOMER") }) {
                Text("Customers owe you", color = Muted, fontSize = 12.sp)
                Text(Money.format(customersOwe, currency), fontWeight = FontWeight.ExtraBold, color = if (customersOwe > 0) Warn else Ink)
            }
            GlassCard(Modifier.weight(1f), onClick = { onNavigate("contacts/SUPPLIER") }) {
                Text("You owe suppliers", color = Muted, fontSize = 12.sp)
                Text(Money.format(weOwe, currency), fontWeight = FontWeight.ExtraBold, color = if (weOwe > 0) Warn else Ink)
            }
        }
        GlassButton("See all money entries", { onNavigate("statement/ALL") }, Modifier.fillMaxWidth())
        Box(Modifier.padding(4.dp))
    }
    if (addAccount) AccountDialog(repo, settings, null) { addAccount = false }
}

@Composable
private fun QuickTile(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, color = Indigo)
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun AccountDialog(repo: Repository, settings: ShopSettings, existing: Account?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currency = Locales.currency(settings.currencyCode)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "CASH") }
    var detail by remember { mutableStateOf(existing?.detail ?: "") }
    var opening by remember { mutableStateOf(existing?.let { Money.toInput(it.openingBalance) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditDialog(
        if (existing == null) "New account" else "Edit account",
        canSave = name.isNotBlank() && (opening.isBlank() || Money.parse(opening) != null),
        onDismiss = onClose,
        onSave = {
            scope.launch {
                repo.saveAccount(existing, name, type, detail, if (opening.isBlank()) 0L else (Money.parse(opening) ?: 0L))
                onClose()
            }
        },
    ) {
        GlassField(name, { name = it }, "Account name *")
        PickerField("Kind", accountTypes[accountCodes.indexOf(type).coerceAtLeast(0)], accountTypes, onPick = { type = accountCodes[accountTypes.indexOf(it)] })
        GlassField(detail, { detail = it }, "Detail (for example bank name and last digits)")
        GlassField(opening, { opening = it }, "Money in it now (${currency.code})", keyboard = KeyboardType.Decimal)
        error?.let { Text(it, color = Bad, fontSize = 13.sp) }
        if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Remove this account", color = Bad) }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog("Remove ${existing.name}?", "Only accounts with no money entries can be removed.", "Remove",
            onConfirm = {
                scope.launch {
                    val result = repo.deleteAccount(existing.id)
                    if (result == null) onClose() else { error = result; confirmDelete = false }
                }
            },
            onDismiss = { confirmDelete = false })
    }
}

/** Adds an expense, income, transfer or owner entry, as in the wireframe's "Money entry". */
@Composable
fun MoneyEntryScreen(repo: Repository, settings: ShopSettings, startKind: String, onBack: () -> Unit) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val types by repo.entryTypes.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()

    var kind by rememberSaveable { mutableStateOf(startKind) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var typeId by remember { mutableStateOf<String?>(null) }
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var putIn by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val first = views.firstOrNull()?.account?.id
    val from = fromId ?: first
    val to = toId ?: views.getOrNull(1)?.account?.id ?: first
    val kindTypes = types.filter { it.kind == kind }
    val chosenType = typeId?.takeIf { id -> kindTypes.any { it.id == id } } ?: kindTypes.firstOrNull()?.id

    fun save() {
        val minor = Money.parse(amount)
        if (minor == null || minor <= 0) { error = "Enter the amount."; return }
        val acc = from ?: run { error = "Choose an account."; return }
        scope.launch {
            val result = when (kind) {
                "EXPENSE" -> repo.recordExpense(acc, chosenType, minor, note)
                "INCOME" -> repo.recordIncome(acc, chosenType, minor, note)
                "OWNER" -> repo.recordOwner(acc, minor, putIn, note)
                else -> repo.recordTransfer(acc, to ?: acc, minor, note)
            }
            if (result == null) onBack() else error = result
        }
    }

    ScreenScaffold(
        "New money entry",
        onBack,
        bottom = {
            GradientButton(
                when (kind) { "TRANSFER" -> "Save transfer"; "EXPENSE" -> "Save expense"; "INCOME" -> "Save income"; else -> "Save" },
                { save() }, Modifier.fillMaxWidth(),
            )
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer", "OWNER" to "Owner")) { (k, label) ->
                GlassChip(label, kind == k) { kind = k; error = null }
            }
        }
        GlassField(amount, { amount = it }, "Amount (${currency.code}) *", keyboard = KeyboardType.Decimal)
        if (kind == "EXPENSE" || kind == "INCOME") {
            PickerField(
                if (kind == "EXPENSE") "Category" else "Type",
                kindTypes.firstOrNull { it.id == chosenType }?.name ?: "",
                kindTypes.map { it.name },
                onPick = { picked -> typeId = kindTypes.first { it.name == picked }.id },
            )
        }
        if (kind == "OWNER") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("I put money in", putIn) { putIn = true }
                GlassChip("I took money out", !putIn) { putIn = false }
            }
        }
        val fromLabel = when (kind) { "EXPENSE" -> "Paid from"; "INCOME" -> "Received into"; "TRANSFER" -> "From"; else -> "Account" }
        SectionLabel(fromLabel)
        AccountChips(views, from, currency) { fromId = it }
        if (kind == "TRANSFER") {
            SectionLabel("To")
            AccountChips(views, to, currency) { toId = it }
            Text(
                "A transfer only moves the shop's own money from one place to another. It is not income and not an expense, so your profit stays the same.",
                color = Muted, fontSize = 13.sp,
            )
        }
        if (kind == "OWNER") {
            Text("Owner money is not income or expense. It only records money you add to or take out of the shop.", color = Muted, fontSize = 13.sp)
        }
        GlassField(note, { note = it }, "Note")
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun AccountChips(views: List<AccountView>, selected: String?, c: CurrencyInfo, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(views) { v ->
            GlassChip("${v.account.name} · ${Money.format(v.balance, c)}", v.account.id == selected) { onPick(v.account.id) }
        }
    }
    val v = views.firstOrNull { it.account.id == selected }
    if (v != null) Text("Has ${Money.format(v.balance, c)}", color = Muted, fontSize = 12.sp)
}

private enum class Period(val label: String) { TODAY("Today"), WEEK("This week"), MONTH("This month"), ALL("All") }

private fun periodStart(p: Period): Long {
    val today = LocalDate.now()
    val d = when (p) {
        Period.TODAY -> today
        Period.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong())
        Period.MONTH -> today.withDayOfMonth(1)
        Period.ALL -> return 0L
    }
    return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** One account's statement, or every account when accountId is ALL. Entries can be cancelled, never erased. */
@Composable
fun StatementScreen(repo: Repository, settings: ShopSettings, accountId: String, onBack: () -> Unit) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val types by repo.entryTypes.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var period by rememberSaveable { mutableStateOf(Period.TODAY) }
    var selected by remember { mutableStateOf<MoneyEntry?>(null) }
    var reason by remember { mutableStateOf("") }
    var editAccount by remember { mutableStateOf(false) }

    val all = accountId == "ALL"
    val view = views.firstOrNull { it.account.id == accountId }
    val names = views.associate { it.account.id to it.account.name }
    val typeNames = types.associate { it.id to it.name }
    val start = periodStart(period)
    val shown = entries.filter { (all || it.accountId == accountId) && it.entryDate >= start }
    val live = shown.filter { it.voidedAt == null }
    val moneyIn = live.filter { it.amount > 0 }.sumOf { it.amount }
    val moneyOut = -live.filter { it.amount < 0 }.sumOf { it.amount }
    val balance = if (all) views.sumOf { it.balance } else view?.balance ?: 0L
    val fmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")

    ScreenScaffold(
        if (all) "All money entries" else view?.account?.name ?: "Account",
        onBack,
        actions = {
            if (view != null) TextButton(onClick = { editAccount = true }) { Text("Edit", color = Indigo, fontWeight = FontWeight.SemiBold) }
        },
    ) {
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Text(if (all) "All shop money" else "Balance now", color = Muted, fontSize = 13.sp)
            Text(Money.format(balance, currency), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text("In (${period.label.lowercase()})", color = Muted, fontSize = 12.sp)
                    Text(Money.format(moneyIn, currency), color = Good, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Out (${period.label.lowercase()})", color = Muted, fontSize = 12.sp)
                    Text(Money.format(moneyOut, currency), color = Bad, fontWeight = FontWeight.Bold)
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Period.values().toList()) { p -> GlassChip(p.label, period == p) { period = p } }
        }
        if (shown.isEmpty()) EmptyCard("Nothing here", "No money moved in this period.")
        shown.forEach { e ->
            val void = e.voidedAt != null
            GlassCard(Modifier.fillMaxWidth(), onClick = { selected = e; reason = "" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeNames[e.typeId] ?: kindLabel(e.kind),
                            fontWeight = FontWeight.SemiBold,
                            color = if (void) Muted else Ink,
                        )
                        val sub = buildList {
                            add(kindLabel(e.kind))
                            if (all) names[e.accountId]?.let { add(it) }
                            if (e.note.isNotBlank()) add(e.note)
                            add(Instant.ofEpochMilli(e.entryDate).atZone(ZoneId.systemDefault()).format(fmt))
                        }.joinToString(" · ")
                        Text(sub, color = Muted, fontSize = 12.sp)
                        if (void) StatusPill("Cancelled" + if (e.voidReason.isNotBlank()) ": ${e.voidReason}" else "", Color(0xFFE4E4E7), Color(0xFF3F3F46))
                    }
                    Text(
                        signed(e.amount, currency),
                        fontWeight = FontWeight.Bold,
                        color = if (void) Muted else if (e.amount > 0) Good else Bad,
                    )
                }
            }
        }
    }

    val e = selected
    if (e != null) {
        if (e.voidedAt != null) {
            EditDialog("Cancelled entry", false, { selected = null }, {}) {
                Text("This entry was cancelled and is not counted. ${e.voidReason}")
            }
        } else {
            EditDialog("Cancel this entry?", true, { selected = null }, {
                scope.launch { repo.voidEntry(e.groupId, reason) }
                selected = null
            }) {
                Text(
                    "${typeNames[e.typeId] ?: kindLabel(e.kind)}: ${signed(e.amount, currency)}. " +
                        "It stays in your records marked Cancelled, and the money goes back.",
                    color = Muted, fontSize = 13.sp,
                )
                GlassField(reason, { reason = it }, "Reason")
            }
        }
    }
    if (editAccount && view != null) AccountDialog(repo, settings, view.account) { editAccount = false }
}

/** Settings > Expense and income types. */
@Composable
fun EntryTypesScreen(repo: Repository, onBack: () -> Unit) {
    val types by repo.entryTypes.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf("EXPENSE") }
    var editing by remember { mutableStateOf<app.phoneshop.manager.data.EntryType?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        "Expense and income types",
        onBack,
        bottom = { GradientButton("+ Add type", { adding = true }, Modifier.fillMaxWidth()) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Expense", kind == "EXPENSE") { kind = "EXPENSE" }
            GlassChip("Income", kind == "INCOME") { kind = "INCOME" }
        }
        types.filter { it.kind == kind }.forEach { t ->
            NavRow(t.name, "", { editing = t })
        }
    }
    if (adding || editing != null) {
        val existing = editing
        var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
        val close = { adding = false; editing = null }
        EditDialog(
            if (existing == null) "New ${kind.lowercase()} type" else "Edit type",
            name.isNotBlank(), close,
            { scope.launch { repo.saveEntryType(existing, name, existing?.kind ?: kind) }; close() },
        ) {
            GlassField(name, { name = it }, "Name")
            if (existing != null) {
                TextButton(onClick = { scope.launch { repo.deleteEntryType(existing.id) }; close() }) {
                    Text("Delete this type", color = Bad)
                }
            }
        }
    }
}
