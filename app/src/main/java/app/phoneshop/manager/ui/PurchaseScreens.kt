package app.phoneshop.manager.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.PoLine
import app.phoneshop.manager.data.PoLineInput
import app.phoneshop.manager.data.Product
import app.phoneshop.manager.data.PurchaseOrder
import app.phoneshop.manager.data.ReceiveLine
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.BillPdf
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

fun poLabel(no: Int) = "PO-" + no.toString().padStart(3, '0')

private fun dayText(millis: Long?): String =
    if (millis == null) "" else java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun statusPill(status: String): Triple<String, Color, Color> = when (status) {
    "DRAFT" -> Triple("Draft", Color(0xFFE4E4E7), Color(0xFF3F3F46))
    "SENT" -> Triple("Sent", Color(0xFFDBEAFE), Color(0xFF1E40AF))
    "PART" -> Triple("Part received", Color(0xFFFFEDD5), Color(0xFF9A3412))
    "RECEIVED" -> Triple("Received", Color(0xFFDCFCE7), Color(0xFF166534))
    else -> Triple("Cancelled", Color(0xFFFEE2E2), Color(0xFF991B1B))
}

/** More → Purchase orders / Purchases and supplier bills. */
@Composable
fun PurchasesScreen(
    repo: Repository,
    settings: ShopSettings,
    startTab: String,
    onBack: () -> Unit,
    onNewOrder: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onReceiveNew: () -> Unit,
) {
    val orders by repo.purchaseOrders.collectAsState(initial = emptyList())
    val purchases by repo.purchases.collectAsState(initial = emptyList())
    val poLines by repo.poLines.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)
    var tab by rememberSaveable { mutableStateOf(startTab) }
    val names = contacts.associate { it.id to it.name }
    val weOwe = contacts.filter { it.kind == SUPPLIER }.sumOf { (balances[it.id] ?: 0L).coerceAtLeast(0) }

    ScreenScaffold(
        "Buying stock",
        onBack,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Receive goods", onReceiveNew, Modifier.weight(1f))
                GradientButton("+ New order", onNewOrder, Modifier.weight(1f))
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Purchase orders", tab == "ORDERS") { tab = "ORDERS" }
            GlassChip("Supplier bills", tab == "BILLS") { tab = "BILLS" }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("You owe suppliers", color = Muted, fontSize = 13.sp)
            Text(Money.format(weOwe, currency), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (weOwe > 0) Warn else Good)
        }
        if (tab == "ORDERS") {
            if (orders.isEmpty()) EmptyCard("No orders yet", "Tap \"+ New order\" to order stock from a supplier.")
            orders.forEach { o ->
                val lines = poLines.filter { it.poId == o.id }
                val pill = statusPill(o.status)
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenOrder(o.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${poLabel(o.poNo)} · ${names[o.supplierId] ?: "Supplier"}", fontWeight = FontWeight.SemiBold)
                            Text("${lines.sumOf { it.qty }} items · ordered ${dayText(o.orderDate)}", color = Muted, fontSize = 12.sp)
                            StatusPill(pill.first, pill.second, pill.third)
                        }
                        Text(Money.format(lines.sumOf { it.qty * it.unitCost }, currency), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        } else {
            if (purchases.isEmpty()) EmptyCard("No supplier bills yet", "Bills appear here when you receive goods.")
            purchases.forEach { p ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(names[p.supplierId] ?: "Supplier", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(if (p.billNo.isNotBlank()) "Bill ${p.billNo}" else "", BillPdf.dateText(p.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
                                color = Muted, fontSize = 12.sp,
                            )
                            if (p.due > 0) StatusPill("Unpaid ${Money.format(p.due, currency)}", Color(0xFFFFEDD5), Color(0xFF9A3412))
                            else StatusPill("Fully paid", Color(0xFFDCFCE7), Color(0xFF166534))
                        }
                        Text(Money.format(p.total, currency), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

private class OrderRow(val productId: String, val name: String, qty: String, cost: String) {
    var qty by mutableStateOf(qty)
    var cost by mutableStateOf(cost)
}

/** Search the product list and pick one. */
@Composable
fun ProductPickerDialog(repo: Repository, onClose: () -> Unit, onPick: (Product) -> Unit) {
    val products by repo.products.collectAsState(initial = emptyList())
    val stock by repo.stock.collectAsState(initial = emptyMap())
    var q by remember { mutableStateOf("") }
    val shown = products.filter { q.isBlank() || it.name.contains(q, true) || it.itemCode.contains(q, true) || it.barcode.contains(q, true) }.take(30)
    EditDialog("Add item", false, onClose, {}) {
        GlassField(q, { q = it }, "Search name or item code")
        if (products.isEmpty()) Text("Add products in the Stock tab first.", color = Muted)
        shown.forEach { p ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onPick(p); onClose() }) {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text("${p.itemCode} · ${stock[p.id]?.qty ?: 0} in stock", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

/** New purchase order, or editing a draft. An order changes no stock and no money. */
@Composable
fun PoFormScreen(repo: Repository, settings: ShopSettings, orderId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val products by repo.products.collectAsState(initial = emptyList())
    val stock by repo.stock.collectAsState(initial = emptyMap())
    val purchaseLines by repo.purchaseLines.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val suppliers = contacts.filter { it.kind == SUPPLIER }
    var supplierId by remember { mutableStateOf<String?>(null) }
    var orderDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var expected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newSupplier by remember { mutableStateOf(false) }
    val rows = remember { mutableStateListOf<OrderRow>() }

    fun lastCost(p: Product): Long = purchaseLines.lastOrNull { it.productId == p.id }?.unitCost ?: stock[p.id]?.maxCost ?: 0L

    androidx.compose.runtime.LaunchedEffect(orderId) {
        if (orderId != null) {
            val o = repo.getOrder(orderId) ?: return@LaunchedEffect
            supplierId = o.supplierId; orderDate = dayText(o.orderDate); expected = dayText(o.expectedDate); note = o.note
            repo.getOrderLines(orderId).forEach { rows.add(OrderRow(it.productId, it.name, it.qty.toString(), Money.toInput(it.unitCost))) }
        }
    }

    val total = rows.sumOf { (it.qty.toIntOrNull() ?: 0) * (Money.parse(it.cost) ?: 0L) }

    fun save(status: String, share: Boolean) {
        val sup = supplierId ?: run { error = "Choose a supplier."; return }
        val lines = rows.mapNotNull { r ->
            val q = r.qty.toIntOrNull() ?: 0
            if (q > 0) PoLineInput(r.productId, r.name, q, Money.parse(r.cost) ?: 0L) else null
        }
        if (lines.isEmpty()) { error = "Add at least one item."; return }
        val od = parseDay(orderDate) ?: run { error = "Write the order date like 2026-10-23."; return }
        val ed = if (expected.isBlank()) null else (parseDay(expected) ?: run { error = "Write the expected date like 2026-10-26."; return })
        scope.launch {
            val id = repo.savePurchaseOrder(orderId, sup, od, ed, note, lines, status)
            if (id == null) { error = "Could not save."; return@launch }
            if (share) {
                val supplierName = suppliers.firstOrNull { it.id == sup }?.name ?: ""
                val text = buildString {
                    appendLine("Purchase order from ${settings.shopName}")
                    appendLine("To: $supplierName")
                    lines.forEach { appendLine("• ${it.name} × ${it.qty} @ ${Money.format(it.unitCost, currency)}") }
                    appendLine("Total: ${Money.format(lines.sumOf { it.qty * it.unitCost }, currency)}")
                    if (ed != null) appendLine("Expected by: ${dayText(ed)}")
                    if (note.isNotBlank()) appendLine(note)
                }
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                context.startActivity(Intent.createChooser(send, "Send order").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            onSaved(id)
        }
    }

    ScreenScaffold(
        if (orderId == null) "New purchase order" else "Edit purchase order",
        onBack,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Save draft", { save("DRAFT", false) }, Modifier.weight(1f))
                GradientButton("Send", { save("SENT", true) }, Modifier.weight(1f))
            }
        },
    ) {
        PickerField(
            "Supplier",
            suppliers.firstOrNull { it.id == supplierId }?.name ?: "Choose supplier",
            suppliers.map { it.name },
            onPick = { picked -> supplierId = suppliers.first { it.name == picked }.id },
        )
        TextButton(onClick = { newSupplier = true }) { Text("+ Add supplier", color = Indigo, fontWeight = FontWeight.SemiBold) }
        GlassField(orderDate, { orderDate = it }, "Order date (YYYY-MM-DD)")
        GlassField(expected, { expected = it }, "Expected delivery (YYYY-MM-DD, optional)")

        SectionLabel("Items")
        rows.forEachIndexed { i, r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { rows.removeAt(i) }) { Text("Remove", color = Bad) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(r.qty, { r.qty = it }, "Quantity", Modifier.weight(1f), keyboard = KeyboardType.Number)
                    GlassField(r.cost, { r.cost = it }, "Cost each", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("+ Add item", { adding = true }, Modifier.weight(1f))
            GlassButton("+ All low-stock items", {
                products.forEach { p ->
                    val have = stock[p.id]?.qty ?: 0
                    if (have <= p.lowStockLevel && rows.none { it.productId == p.id }) {
                        rows.add(OrderRow(p.id, p.name, maxOf(1, p.lowStockLevel * 2 - have).toString(), Money.toInput(lastCost(p))))
                    }
                }
            }, Modifier.weight(1f))
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row {
                Text("Order total", Modifier.weight(1f), color = Muted)
                Text(Money.format(total, currency), fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("An order does not change stock or money. That only happens when the goods are received.", color = Muted, fontSize = 13.sp)
        GlassField(note, { note = it }, "Note to supplier")
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
    if (adding) ProductPickerDialog(repo, { adding = false }) { p ->
        if (rows.none { it.productId == p.id }) rows.add(OrderRow(p.id, p.name, "1", Money.toInput(lastCost(p))))
    }
    if (newSupplier) QuickCustomerDialog(repo, kind = SUPPLIER, onClose = { newSupplier = false }) { id -> supplierId = id }
}

/** One order: what was ordered, what arrived, and the buttons that move it along. */
@Composable
fun PoDetailScreen(
    repo: Repository,
    settings: ShopSettings,
    orderId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReceive: () -> Unit,
) {
    val order by repo.observePurchaseOrder(orderId).collectAsState(initial = null)
    val allLines by repo.poLines.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf(false) }
    val o = order
    val lines = allLines.filter { it.poId == orderId }

    ScreenScaffold(if (o != null) poLabel(o.poNo) else "Order", onBack) {
        if (o == null) return@ScreenScaffold
        val pill = statusPill(o.status)
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Text(contacts.firstOrNull { it.id == o.supplierId }?.name ?: "Supplier", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            StatusPill(pill.first, pill.second, pill.third)
            Text("Ordered ${dayText(o.orderDate)}" + if (o.expectedDate != null) " · expected ${dayText(o.expectedDate)}" else "", color = Muted, fontSize = 13.sp)
            if (o.note.isNotBlank()) Text(o.note, color = Muted, fontSize = 13.sp)
        }
        lines.forEach { l ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold)
                        Text("${l.receivedQty} of ${l.qty} received · ${Money.format(l.unitCost, currency)} each", color = Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(l.qty * l.unitCost, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row {
                Text("Order total", Modifier.weight(1f), color = Muted)
                Text(Money.format(lines.sumOf { it.qty * it.unitCost }, currency), fontWeight = FontWeight.ExtraBold)
            }
        }
        val open = o.status == "DRAFT" || o.status == "SENT" || o.status == "PART"
        if (open) GradientButton("Receive goods", onReceive, Modifier.fillMaxWidth())
        if (o.status == "DRAFT") {
            GlassButton("Edit order", onEdit, Modifier.fillMaxWidth())
            GlassButton("Mark as sent", { scope.launch { repo.setOrderStatus(orderId, "SENT") } }, Modifier.fillMaxWidth())
        }
        if (open) GlassButton("Cancel order", { confirmCancel = true }, Modifier.fillMaxWidth(), danger = true)
    }
    if (confirmCancel) ConfirmDialog("Cancel this order?", "Nothing already received is changed.", "Cancel order",
        onConfirm = { scope.launch { repo.setOrderStatus(orderId, "CANCELLED") }; confirmCancel = false },
        onDismiss = { confirmCancel = false })
}

private class ReceiveRow(
    val productId: String,
    val name: String,
    val tracksImei: Boolean,
    val poLineId: String?,
    qty: String,
    cost: String,
    val ordered: Int = 0,
) {
    var qty by mutableStateOf(qty)
    var cost by mutableStateOf(cost)
    var imeis by mutableStateOf("")
}

/** Goods arrive: tick what came, enter IMEIs, record the supplier bill and any payment. */
@Composable
fun ReceiveScreen(repo: Repository, settings: ShopSettings, orderId: String?, onBack: () -> Unit) {
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val products by repo.products.collectAsState(initial = emptyList())
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()

    val suppliers = contacts.filter { it.kind == SUPPLIER }
    var supplierId by remember { mutableStateOf<String?>(null) }
    var billNo by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var payBy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newSupplier by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val rows = remember { mutableStateListOf<ReceiveRow>() }

    fun tracks(p: Product?) = p != null && categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true

    androidx.compose.runtime.LaunchedEffect(orderId, products.size, categories.size) {
        if (orderId != null && rows.isEmpty() && products.isNotEmpty()) {
            val o = repo.getOrder(orderId) ?: return@LaunchedEffect
            supplierId = o.supplierId
            repo.getOrderLines(orderId).forEach { l ->
                val left = l.qty - l.receivedQty
                if (left > 0) {
                    rows.add(ReceiveRow(l.productId, l.name, tracks(products.firstOrNull { it.id == l.productId }), l.id, left.toString(), Money.toInput(l.unitCost), l.qty))
                }
            }
        }
    }

    val total = rows.sumOf { (it.qty.toIntOrNull() ?: 0) * (Money.parse(it.cost) ?: 0L) }
    val paidMinor = if (paid.isBlank()) 0L else (Money.parse(paid) ?: 0L)
    val chosenAccount = accountId ?: views.firstOrNull()?.account?.id

    fun save() {
        val sup = supplierId ?: run { error = "Choose a supplier."; return }
        val lines = rows.map { r ->
            ReceiveLine(
                r.productId, r.name, r.qty.toIntOrNull() ?: 0, Money.parse(r.cost) ?: 0L, r.tracksImei,
                r.imeis.split("\n", ",", " ", ";").map { it.trim() }.filter { it.isNotEmpty() }, r.poLineId,
            )
        }
        val by = if (payBy.isBlank()) null else (parseDay(payBy) ?: run { error = "Write the pay-by date like 2026-11-06."; return })
        saving = true
        scope.launch {
            val result = repo.receiveGoods(orderId, sup, billNo, lines, chosenAccount, paidMinor, by)
            saving = false
            if (result == null) onBack() else error = result
        }
    }

    ScreenScaffold(
        "Receive goods",
        onBack,
        bottom = { GradientButton(if (saving) "Saving..." else "Save and add to stock", { save() }, Modifier.fillMaxWidth(), enabled = !saving) },
    ) {
        if (orderId == null) {
            PickerField(
                "Supplier",
                suppliers.firstOrNull { it.id == supplierId }?.name ?: "Choose supplier",
                suppliers.map { it.name },
                onPick = { picked -> supplierId = suppliers.first { it.name == picked }.id },
            )
            TextButton(onClick = { newSupplier = true }) { Text("+ Add supplier", color = Indigo, fontWeight = FontWeight.SemiBold) }
        } else {
            Text(
                "${suppliers.firstOrNull { it.id == supplierId }?.name ?: ""}: type what actually arrived.",
                color = Muted,
            )
        }
        rows.forEachIndexed { i, r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold)
                        if (r.ordered > 0) Text("ordered ${r.ordered}", color = Muted, fontSize = 12.sp)
                    }
                    if (orderId == null) TextButton(onClick = { rows.removeAt(i) }) { Text("Remove", color = Bad) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(r.qty, { r.qty = it }, "Received", Modifier.weight(1f), keyboard = KeyboardType.Number)
                    GlassField(r.cost, { r.cost = it }, "Cost each", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
                if (r.tracksImei) {
                    OutlinedImeiField(r.imeis, { r.imeis = it }, (r.qty.toIntOrNull() ?: 0))
                }
                val missing = r.ordered - (r.qty.toIntOrNull() ?: 0)
                if (r.ordered > 0 && missing > 0) Text("$missing missing: they stay on the order as \"to come\".", color = Muted, fontSize = 12.sp)
            }
        }
        if (orderId == null) GlassButton("+ Add item", { adding = true }, Modifier.fillMaxWidth())

        SectionLabel("Supplier bill")
        GlassField(billNo, { billNo = it }, "Supplier bill no.")
        GlassCard(Modifier.fillMaxWidth()) {
            Row { Text("Bill total", Modifier.weight(1f), color = Muted); Text(Money.format(total, currency), fontWeight = FontWeight.ExtraBold) }
        }
        SectionLabel("Payment now")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(views) { v -> GlassChip("${v.account.name} · ${Money.format(v.balance, currency)}", v.account.id == chosenAccount) { accountId = v.account.id } }
        }
        GlassField(paid, { paid = it }, "Paid now (${currency.code}), leave empty to pay later", keyboard = KeyboardType.Decimal)
        GlassCard(Modifier.fillMaxWidth()) {
            Row { Text("Added to what you owe", Modifier.weight(1f), color = Muted); Text(Money.format((total - paidMinor).coerceAtLeast(0), currency), fontWeight = FontWeight.ExtraBold, color = Warn) }
        }
        if (total - paidMinor > 0) GlassField(payBy, { payBy = it }, "Pay by (YYYY-MM-DD, optional)")
        supplierId?.let { Text("You owe this supplier ${Money.format(balances[it] ?: 0L, currency)} now.", color = Muted, fontSize = 12.sp) }
        Text(
            "On save: stock goes up, each phone gets its IMEI and cost, the account balance goes down by what you paid, and the supplier balance goes up by the rest.",
            color = Muted, fontSize = 13.sp,
        )
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
    if (adding) ProductPickerDialog(repo, { adding = false }) { p ->
        if (rows.none { it.productId == p.id }) rows.add(ReceiveRow(p.id, p.name, tracks(p), null, "1", ""))
    }
    if (newSupplier) QuickCustomerDialog(repo, kind = SUPPLIER, onClose = { newSupplier = false }) { id -> supplierId = id }
}

@Composable
private fun OutlinedImeiField(value: String, onChange: (String) -> Unit, expected: Int) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("IMEIs: one per line ($expected needed)") },
        minLines = 2,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
