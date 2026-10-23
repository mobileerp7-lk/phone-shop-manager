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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Product
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.SaleLine
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.BillPdf
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

/** Take items back from a bill: refund from an account or take it off what the customer owes. */
@Composable
fun ReturnScreen(repo: Repository, settings: ShopSettings, saleId: String, onBack: () -> Unit) {
    val lines by repo.observeSaleLines(saleId).collectAsState(initial = emptyList())
    val sale by repo.observeSale(saleId).collectAsState(initial = null)
    val returns by repo.saleReturns.collectAsState(initial = emptyList())
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<SaleLine?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Return items", onBack) {
        Text("Choose what the customer is bringing back from bill #${sale?.billNo ?: ""}.", color = Muted)
        lines.filter { it.kind == "PRODUCT" }.forEach { l ->
            val back = returns.filter { it.lineId == l.id }.sumOf { it.qty }
            val left = l.qty - back
            GlassCard(Modifier.fillMaxWidth(), onClick = if (left > 0) ({ picked = l; error = null }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold)
                        Text(if (left > 0) "$left of ${l.qty} can be returned" else "Already returned", color = Muted, fontSize = 12.sp)
                        if (l.imei.isNotBlank()) Text("IMEI ${l.imei}", color = Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(l.unitPrice, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (lines.none { it.kind == "PRODUCT" }) EmptyCard("Nothing to return", "This bill has no products. Repairs and services cannot be returned here.")
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }

    picked?.let { l ->
        val back = returns.filter { it.lineId == l.id }.sumOf { it.qty }
        var qty by remember(l) { mutableStateOf("1") }
        var refund by remember(l) { mutableStateOf(Money.toInput(l.unitPrice)) }
        var restock by remember(l) { mutableStateOf(true) }
        var toBalance by remember(l) { mutableStateOf(false) }
        var accountId by remember(l) { mutableStateOf<String?>(null) }
        var reason by remember(l) { mutableStateOf("") }
        val acc = accountId ?: views.firstOrNull()?.account?.id
        EditDialog("Return ${l.name}", Money.parse(refund) != null && (qty.toIntOrNull() ?: 0) > 0, { picked = null }, {
            scope.launch {
                val result = repo.returnItems(
                    saleId, l.id, qty.toIntOrNull() ?: 0, Money.parse(refund) ?: 0L, restock,
                    if (toBalance) "BALANCE" else "CASH", acc, reason,
                )
                if (result == null) picked = null else error = result
            }
        }) {
            if (l.qty - back > 1) GlassField(qty, { qty = it; Money.parse(refund)?.let { } }, "How many", keyboard = KeyboardType.Number)
            GlassField(refund, { refund = it }, "Refund amount (${currency.code})", keyboard = KeyboardType.Decimal)
            SwitchRow("Put back in stock", "Turn off if it is damaged.", restock, { restock = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Pay back from account", !toBalance) { toBalance = false }
                if (sale?.customerId != null) GlassChip("Take off what they owe", toBalance) { toBalance = true }
            }
            if (!toBalance) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(views) { v -> GlassChip(v.account.name, v.account.id == acc) { accountId = v.account.id } }
            }
            GlassField(reason, { reason = it }, "Reason")
            error?.let { Text(it, color = Bad, fontSize = 13.sp) }
        }
    }
}

/** Buy a used phone from a customer. It goes into stock with its IMEI and the price you paid. */
@Composable
fun TradeInScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var picking by remember { mutableStateOf(false) }
    var imei1 by remember { mutableStateOf("") }
    var imei2 by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var seller by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var newCustomer by remember { mutableStateOf(false) }
    val customers = contacts.filter { it.kind == CUSTOMER }
    val acc = accountId ?: views.firstOrNull()?.account?.id

    ScreenScaffold(
        "Trade-in",
        onBack,
        bottom = {
            GradientButton("Add to stock", {
                val p = product
                val cost = Money.parse(price)
                when {
                    p == null -> error = "Choose the phone model. Add it in Stock first if it is new."
                    cost == null -> error = "Enter the price you pay."
                    acc == null -> error = "Choose the account you pay from."
                    else -> scope.launch {
                        val r = repo.tradeIn(p.id, imei1, imei2, cost, acc, seller, note.ifBlank { p.name })
                        if (r == null) onBack() else error = r
                    }
                }
            }, Modifier.fillMaxWidth())
        },
    ) {
        Text("You buy a used phone from a customer. It is added to your stock and the money leaves the account you choose.", color = Muted, fontSize = 13.sp)
        PickerField("Seller (customer)", customers.firstOrNull { it.id == seller }?.name ?: "Walk-in", listOf("Walk-in") + customers.map { it.name },
            onPick = { seller = customers.firstOrNull { c -> c.name == it }?.id })
        TextButton(onClick = { newCustomer = true }) { Text("+ Add customer", color = Indigo, fontWeight = FontWeight.SemiBold) }
        GlassButton(product?.name ?: "Choose phone model", { picking = true }, Modifier.fillMaxWidth())
        GlassField(imei1, { imei1 = it }, "IMEI 1", keyboard = KeyboardType.Number)
        GlassField(imei2, { imei2 = it }, "IMEI 2 (optional)", keyboard = KeyboardType.Number)
        GlassField(price, { price = it }, "Price you pay (${currency.code})", keyboard = KeyboardType.Decimal)
        SectionLabel("Pay from")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(views) { v -> GlassChip(v.account.name, v.account.id == acc) { accountId = v.account.id } }
        }
        GlassField(note, { note = it }, "Note (checks done, condition)")
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
    if (picking) ProductPickerDialog(repo, { picking = false }) { p ->
        if (categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true) { product = p; error = null }
        else error = "Choose a product that is tracked by IMEI (a phone)."
    }
    if (newCustomer) QuickCustomerDialog(repo, onClose = { newCustomer = false }) { id -> seller = id }
}

private val adjustReasons = listOf("Damaged", "Lost", "Count correction", "Other")

/** Remove lost or damaged stock (its cost counts as a loss), or add stock you found. */
@Composable
fun StockAdjustScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val categories by repo.categories.collectAsState(initial = emptyList())
    val history by repo.adjustments.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var picking by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(true) }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    var unitId by remember { mutableStateOf<String?>(null) }
    var reasonKind by remember { mutableStateOf("Damaged") }
    var reasonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val tracks = product?.let { p -> categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true } == true
    val units by (product?.id?.let { repo.observeUnits(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    ScreenScaffold(
        "Stock adjustments",
        onBack,
        bottom = {
            GradientButton("Save adjustment", {
                val p = product
                val n = qty.toIntOrNull() ?: 0
                when {
                    p == null -> error = "Choose a product."
                    n <= 0 && !tracks -> error = "Enter how many."
                    !remove && Money.parse(cost) == null -> error = "Enter the buying price of what you add."
                    else -> scope.launch {
                        val r = repo.adjustStock(p.id, p.name, tracks, if (remove) (if (tracks) -1 else -n) else n, unitId, Money.parse(cost) ?: 0L, "$reasonKind ${reasonText}".trim())
                        if (r == null) { product = null; qty = "1"; cost = ""; unitId = null; reasonText = ""; error = null } else error = r
                    }
                }
            }, Modifier.fillMaxWidth())
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Remove stock", remove) { remove = true }
            GlassChip("Add stock", !remove) { remove = false }
        }
        GlassButton(product?.name ?: "Choose product", { picking = true }, Modifier.fillMaxWidth())
        if (tracks && remove) {
            SectionLabel("Which phone?")
            units.filter { it.status == "IN_STOCK" }.forEach { u ->
                GlassChip("IMEI ${u.imei1}", u.id == unitId) { unitId = u.id }
            }
        } else {
            GlassField(qty, { qty = it }, "How many", keyboard = KeyboardType.Number)
        }
        if (!remove) GlassField(cost, { cost = it }, "Buying price each (${currency.code})", keyboard = KeyboardType.Decimal)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(adjustReasons) { r -> GlassChip(r, reasonKind == r) { reasonKind = r } }
        }
        GlassField(reasonText, { reasonText = it }, "Note")
        Text(
            if (remove) "Removed stock is counted as a loss at the price it cost you." else "Use this only for stock you found. For new purchases use Receive goods.",
            color = Muted, fontSize = 13.sp,
        )
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
        if (history.isNotEmpty()) {
            SectionLabel("Recent adjustments")
            history.take(10).forEach { a ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("${a.name} ×${a.qty}", fontWeight = FontWeight.SemiBold)
                            Text("${a.reason} · ${BillPdf.dateText(a.createdAt)}", color = Muted, fontSize = 12.sp)
                        }
                        Text("− " + Money.format(a.costTotal, currency), color = Bad, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (picking) ProductPickerDialog(repo, { picking = false }) { product = it; unitId = null }
}

/** Held sales and quotations. Tap one to bring it back into the cart. */
@Composable
fun QuotesScreen(repo: Repository, settings: ShopSettings, cart: CartState, onBack: () -> Unit, onOpenCart: () -> Unit) {
    val quotes by repo.quotes.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenScaffold("Held sales and quotations", onBack) {
        if (quotes.isEmpty()) EmptyCard("Nothing saved", "In Sell, use Hold to park a sale, or Quotation to give a price offer.")
        quotes.forEach { q ->
            val who = contacts.firstOrNull { it.id == q.customerId }?.name ?: "Walk-in"
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (q.kind == "QUOTE") "Quotation Q-" else "Held H-") + q.number.toString().padStart(3, '0'), fontWeight = FontWeight.SemiBold)
                        Text("$who · ${BillPdf.dateText(q.createdAt)}", color = Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(q.total, currency), fontWeight = FontWeight.ExtraBold)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Open in cart", {
                        cart.clear()
                        repo.quoteLines(q).forEach { cart.lines.add(it) }
                        cart.customerId = q.customerId
                        cart.discount = if (q.discount > 0) Money.toInput(q.discount) else ""
                        if (q.kind == "HELD") scope.launch { repo.deleteQuote(q.id) }
                        onOpenCart()
                    }, Modifier.weight(1f))
                    if (q.kind == "QUOTE") GlassButton("Share", {
                        val lines = repo.quoteLines(q)
                        val text = buildString {
                            appendLine("Quotation from ${settings.shopName}")
                            lines.forEach { appendLine("• ${it.name} × ${it.qty}: ${Money.format(it.unitPrice * it.qty, currency)}") }
                            if (q.discount > 0) appendLine("Discount: ${Money.format(q.discount, currency)}")
                            appendLine("Total: ${Money.format(q.total, currency)}")
                        }
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(send, "Send quotation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { repo.deleteQuote(q.id) } }) { Text("Delete", color = Bad) }
                }
            }
        }
    }
}
