package app.phoneshop.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
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
import app.phoneshop.manager.data.Contact
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

const val CUSTOMER = "CUSTOMER"
const val SUPPLIER = "SUPPLIER"

private fun kindLabel(kind: String) = if (kind == SUPPLIER) "supplier" else "customer"

/** Customers and suppliers in one list with two tabs, as in the wireframe. */
@Composable
fun ContactsScreen(
    repo: Repository,
    settings: ShopSettings,
    startKind: String,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)
    var kind by rememberSaveable { mutableStateOf(startKind) }
    var query by rememberSaveable { mutableStateOf("") }
    var oweOnly by rememberSaveable { mutableStateOf(false) }

    val ofKind = contacts.filter { it.kind == kind }
    val shown = ofKind.filter {
        (!oweOnly || (balances[it.id] ?: 0L) > 0) &&
            (query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true))
    }
    val totalOwed = ofKind.sumOf { (balances[it.id] ?: 0L).coerceAtLeast(0) }
    val owingCount = ofKind.count { (balances[it.id] ?: 0L) > 0 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar("Customers and suppliers", onBack = onBack)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Customers", kind == CUSTOMER) { kind = CUSTOMER; oweOnly = false }
                    GlassChip("Suppliers", kind == SUPPLIER) { kind = SUPPLIER; oweOnly = false }
                }
                GlassField(query, { query = it }, "Search name or phone")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("All", !oweOnly) { oweOnly = false }
                    GlassChip(if (kind == CUSTOMER) "Owe money ($owingCount)" else "You owe ($owingCount)", oweOnly) { oweOnly = !oweOnly }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(if (kind == CUSTOMER) "Customers owe you" else "You owe suppliers", color = Muted, fontSize = 13.sp)
                    Text(Money.format(totalOwed, currency), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (totalOwed > 0) Warn else Good)
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    EmptyCard(
                        if (ofKind.isEmpty()) "No ${kindLabel(kind)}s yet" else "Nothing matches",
                        if (ofKind.isEmpty()) "Tap \"Add ${kindLabel(kind)}\" to add the first one." else "Try another word or clear the filter.",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                        GlassCard(Modifier.fillMaxWidth().then(if (i < 8) Modifier.riseIn(i) else Modifier), onClick = { onOpen(c.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(c.name)
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    if (c.phone.isNotBlank()) Text(c.phone, color = Muted, fontSize = 13.sp)
                                }
                                val bal = balances[c.id] ?: 0L
                                Text(
                                    Money.format(bal, currency),
                                    fontWeight = FontWeight.Bold,
                                    color = if (bal > 0) Warn else Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
        GradientButton(
            "+ Add ${kindLabel(kind)}",
            { onAdd(kind) },
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun Avatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(Modifier.size(44.dp).background(Indigo.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(initials.ifEmpty { "?" }, color = Indigo, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ContactFormScreen(
    repo: Repository,
    settings: ShopSettings,
    kind: String,
    contactId: String?,
    onBack: () -> Unit,
) {
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<Contact?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(contactId) {
        if (contactId != null) {
            val c = repo.getContact(contactId)
            if (c != null) {
                existing = c; name = c.name; phone = c.phone; address = c.address; note = c.note
                opening = if (c.openingBalance != 0L) Money.toInput(c.openingBalance) else ""
            }
        }
    }
    val label = kindLabel(existing?.kind ?: kind)

    ScreenScaffold(
        if (contactId == null) "Add $label" else "Edit $label",
        onBack,
        bottom = {
            GradientButton("Save", {
                val bal = if (opening.isBlank()) 0L else Money.parse(opening)
                when {
                    name.isBlank() -> error = "Please enter a name."
                    bal == null -> error = "The amount owed is not a number."
                    else -> scope.launch {
                        repo.saveContact(existing, existing?.kind ?: kind, name, phone, address, note, bal)
                        onBack()
                    }
                }
            }, Modifier.fillMaxWidth())
        },
    ) {
        GlassField(name, { name = it }, "Name *")
        GlassField(phone, { phone = it }, "Phone / WhatsApp", keyboard = KeyboardType.Phone)
        GlassField(address, { address = it }, "Address")
        GlassField(note, { note = it }, "Note")
        GlassField(
            opening, { opening = it },
            if ((existing?.kind ?: kind) == CUSTOMER) "Already owes you (${currency.code}), optional" else "You already owe them (${currency.code}), optional",
            keyboard = KeyboardType.Decimal,
        )
        Text("Use this only for money owed before you started using the app.", color = Muted, fontSize = 13.sp)
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
        if (existing != null) {
            GlassButton("Delete $label", { confirmDelete = true }, Modifier.fillMaxWidth(), danger = true)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this $label?",
            "Their past bills stay in your records.",
            "Delete",
            onConfirm = { scope.launch { repo.deleteContact(contactId ?: return@launch); onBack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The profile page: what they owe, quick actions and a statement-style history. */
@Composable
fun ContactDetailScreen(
    repo: Repository,
    settings: ShopSettings,
    contactId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCollect: () -> Unit,
    onSell: () -> Unit,
    onOpenBill: (String) -> Unit,
) {
    val contact by repo.observeContact(contactId).collectAsState(initial = null)
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val sales by repo.sales.collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val c = contact
    ScreenScaffold(
        if (c?.kind == SUPPLIER) "Supplier profile" else "Customer profile",
        onBack,
        actions = { TextButton(onClick = onEdit, enabled = c != null) { Text("Edit", color = Indigo, fontWeight = FontWeight.SemiBold) } },
    ) {
        if (c == null) return@ScreenScaffold
        val owed = balances[c.id] ?: 0L
        val mySales = sales.filter { it.customerId == c.id && it.status == "DONE" }
        val payments = entries.filter { it.contactId == c.id && it.voidedAt == null && (it.kind == "DEBT_PAID" || it.kind == "SUPPLIER_PAID") }
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(c.name)
                Column {
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp)
                }
            }
            if (c.note.isNotBlank()) Text(c.note, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(1)) {
            Text(if (c.kind == SUPPLIER) "You owe them" else "Owes you", color = Muted, fontSize = 13.sp)
            Text(Money.format(owed, currency), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = if (owed > 0) Warn else Good)
            c.promiseDate?.let { if (owed > 0) Text("Promised by ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", color = Muted, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton(if (c.kind == SUPPLIER) "Pay supplier" else "Collect", onCollect, Modifier.weight(1f), enabled = owed > 0)
            if (c.kind == CUSTOMER) GlassButton("New sale", onSell, Modifier.weight(1f))
        }
        if (c.kind == CUSTOMER) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(Modifier.weight(1f)) {
                    Text("Total bought", color = Muted, fontSize = 12.sp)
                    Text(Money.format(mySales.sumOf { it.total }, currency), fontWeight = FontWeight.Bold)
                }
                GlassCard(Modifier.weight(1f)) {
                    Text("Bills", color = Muted, fontSize = 12.sp)
                    Text(mySales.size.toString(), fontWeight = FontWeight.Bold)
                }
            }
        }
        SectionLabel("History (like a statement)")
        data class Item(val time: Long, val title: String, val sub: String, val change: Long, val billId: String?)
        val items = buildList {
            if (c.openingBalance != 0L) add(Item(c.createdAt, "Opening balance", "from before the app", c.openingBalance, null))
            mySales.forEach { s -> add(Item(s.createdAt, "Bill #${s.billNo}", "total ${Money.format(s.total, currency)} · paid ${Money.format(s.paid, currency)}", s.due, s.id)) }
            payments.forEach { e -> add(Item(e.entryDate, if (c.kind == SUPPLIER) "Paid supplier" else "Payment received", e.note, -kotlin.math.abs(e.amount), null)) }
        }.sortedByDescending { it.time }
        if (items.isEmpty()) EmptyCard("Nothing yet", "Bills and payments will appear here.")
        items.forEach { i ->
            GlassCard(Modifier.fillMaxWidth(), onClick = i.billId?.let { id -> { onOpenBill(id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf(BillPdfDate(i.time), i.sub).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                    Text(
                        (if (i.change > 0) "+ " else if (i.change < 0) "− " else "") + Money.format(kotlin.math.abs(i.change), currency),
                        fontWeight = FontWeight.Bold,
                        color = if (i.change > 0) Warn else if (i.change < 0) Good else Muted,
                    )
                }
            }
        }
        Text("+ means they owe more, − means they paid some back.", color = Muted, fontSize = 12.sp)
    }
}

private fun BillPdfDate(millis: Long): String = app.phoneshop.manager.util.BillPdf.dateText(millis)

/** Collect an old debt from a customer, or pay a supplier. Same screen, reversed. */
@Composable
fun SettleScreen(repo: Repository, settings: ShopSettings, contactId: String, onBack: () -> Unit) {
    val contact by repo.observeContact(contactId).collectAsState(initial = null)
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var promise by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val c = contact
    val supplier = c?.kind == SUPPLIER
    val owed = balances[contactId] ?: 0L
    val minor = Money.parse(amount) ?: 0L
    val chosen = accountId ?: views.firstOrNull()?.account?.id

    ScreenScaffold(
        if (supplier) "Pay supplier" else "Collect payment",
        onBack,
        bottom = {
            GradientButton("Save payment", {
                val acc = chosen
                val promiseMs = if (promise.isBlank()) null else parseDay(promise)
                when {
                    acc == null -> error = "Choose an account."
                    promise.isNotBlank() && promiseMs == null -> error = "Write the date like 2026-11-06."
                    else -> scope.launch {
                        val result = repo.settleBalance(contactId, supplier, acc, minor, promiseMs, "")
                        if (result == null) onBack() else error = result
                    }
                }
            }, Modifier.fillMaxWidth())
        },
    ) {
        if (c == null) return@ScreenScaffold
        GlassCard(Modifier.fillMaxWidth()) {
            Text(if (supplier) "You owe ${c.name}" else "${c.name} owes you now", color = Muted, fontSize = 13.sp)
            Text(Money.format(owed, currency), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Warn)
        }
        GlassField(amount, { amount = it }, if (supplier) "Amount paid (${currency.code})" else "Amount received (${currency.code})", keyboard = KeyboardType.Decimal)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Full ${Money.format(owed, currency)}", false) { amount = Money.toInput(owed) }
            GlassChip("Half", false) { amount = Money.toInput(owed / 2) }
        }
        SectionLabel(if (supplier) "Paid from" else "Received into")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(views) { v -> GlassChip(v.account.name, v.account.id == chosen) { accountId = v.account.id } }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(if (supplier) "Still owed after this" else "Still owes after this", color = Muted, fontSize = 13.sp)
            Text(Money.format((owed - minor).coerceAtLeast(0), currency), fontWeight = FontWeight.ExtraBold)
        }
        if (!supplier) GlassField(promise, { promise = it }, "New promise date (optional, like 2026-11-06)")
        Text(
            if (supplier) "Paying a supplier moves money out of your account. It is not an expense, because the purchase was already counted."
            else "Collecting old debt is not new income. The sale was counted on the day it was made. It only moves money into your account.",
            color = Muted, fontSize = 13.sp,
        )
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
}
