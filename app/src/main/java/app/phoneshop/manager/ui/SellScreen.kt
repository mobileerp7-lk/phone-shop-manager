package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.CartLine
import app.phoneshop.manager.data.Product
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.Service
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import java.util.UUID

/** The selling screen: search, add to the cart, discount, customer, then take payment. */
@Composable
fun SellScreen(
    repo: Repository,
    settings: ShopSettings,
    cart: CartState,
    onPay: () -> Unit,
) {
    val products by repo.products.collectAsState(initial = emptyList())
    val services by repo.services.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val stock by repo.stock.collectAsState(initial = emptyMap())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("PRODUCTS") }
    var message by remember { mutableStateOf<String?>(null) }
    var pickPhoneFor by remember { mutableStateOf<Product?>(null) }
    var editKey by remember { mutableStateOf<String?>(null) }
    var newCustomer by remember { mutableStateOf(false) }
    var serviceToPrice by remember { mutableStateOf<Service?>(null) }

    val q = query.trim()
    val imeiMatches by produceState(emptyList<String>(), query) {
        value = if (q.length >= 4) repo.productIdsByImei(q) else emptyList()
    }
    val productResults = if (tab != "PRODUCTS") emptyList() else products.filter { p ->
        q.isEmpty() || p.name.contains(q, true) || p.itemCode.contains(q, true) ||
            p.barcode.contains(q, true) || p.attributesJson.contains(q, true) || p.id in imeiMatches
    }.take(if (q.isEmpty()) 8 else 20)
    val serviceResults = if (tab != "SERVICES") emptyList() else services.filter { q.isEmpty() || it.name.contains(q, true) }

    val discountMinor = Money.parse(cart.discount) ?: 0L
    val subtotal = cart.subtotal
    val total = (subtotal - discountMinor).coerceAtLeast(0)
    val customers = contacts.filter { it.kind == CUSTOMER }
    val itemCount = cart.lines.sumOf { it.qty }

    fun addProduct(p: Product, unitId: String?, imei: String) {
        val tracks = categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true
        if (tracks) {
            cart.lines.add(CartLine(UUID.randomUUID().toString(), "PRODUCT", p.id, p.name, 1, p.sellPrice, unitId, imei, p.warrantyMonths, true, p.minPrice))
            return
        }
        val inCart = cart.lines.indexOfFirst { it.productId == p.id }
        val available = stock[p.id]?.qty ?: 0
        val already = if (inCart >= 0) cart.lines[inCart].qty else 0
        if (already + 1 > available) { message = "${p.name}: only $available in stock."; return }
        message = null
        if (inCart >= 0) cart.lines[inCart] = cart.lines[inCart].copy(qty = already + 1)
        else cart.lines.add(CartLine(UUID.randomUUID().toString(), "PRODUCT", p.id, p.name, 1, p.sellPrice, null, "", 0, false, p.minPrice))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New sale", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            if (cart.lines.isNotEmpty()) TextButton(onClick = { cart.clear() }) { Text("Clear", color = Bad) }
        }
        GlassField(query, { query = it }, "Search item, item code, IMEI or barcode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Products", tab == "PRODUCTS") { tab = "PRODUCTS" }
            GlassChip("Services", tab == "SERVICES") { tab = "SERVICES" }
        }
        message?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }

        productResults.forEach { p ->
            val qty = stock[p.id]?.qty ?: 0
            GlassCard(Modifier.fillMaxWidth(), onClick = {
                val tracks = categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true
                if (qty <= 0) message = "${p.name} is out of stock."
                else if (tracks) pickPhoneFor = p
                else addProduct(p, null, "")
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(p, Modifier.padding(end = 10.dp).size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text("${p.itemCode} · $qty in stock", color = if (qty == 0) Bad else Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(p.sellPrice, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        serviceResults.forEach { s ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { serviceToPrice = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(Money.format(s.price, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == "SERVICES" && services.isEmpty()) {
            Text("No services yet. Add them in More → Settings → Services and charges.", color = Muted, fontSize = 13.sp)
        }

        SectionLabel("Cart")
        if (cart.lines.isEmpty()) {
            Text("Tap an item above to add it.", color = Muted)
        }
        cart.lines.forEach { l ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { editKey = l.key }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold)
                        val sub = buildList {
                            if (l.imei.isNotBlank()) add("IMEI ${l.imei}")
                            if (l.warrantyMonths > 0) add("${l.warrantyMonths} months warranty")
                            if (l.qty > 1) add("${Money.format(l.unitPrice, currency)} each")
                        }.joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp)
                        if (l.minPrice > 0 && l.unitPrice < l.minPrice) Text("Below the lowest allowed price", color = Warn, fontSize = 12.sp)
                    }
                    Text(if (l.qty > 1) "×${l.qty}  " else "", color = Muted)
                    Text(Money.format(l.unitPrice * l.qty, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (cart.lines.isNotEmpty()) Text("Tap a line to change price, quantity or remove", color = Muted, fontSize = 12.sp)

        if (cart.lines.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                TotalRow("Subtotal ($itemCount items)", Money.format(subtotal, currency))
                GlassField(cart.discount, { cart.discount = it }, "Discount (${currency.code})", keyboard = KeyboardType.Decimal)
                if (discountMinor > subtotal) Text("The discount is more than the bill.", color = Bad, fontSize = 12.sp)
                if (settings.taxEnabled && settings.taxRateBp > 0) {
                    val tax = total * settings.taxRateBp / (10000 + settings.taxRateBp)
                    TotalRow("Tax (included)", Money.format(tax, currency))
                }
                TotalRow("Total", Money.format(total, currency), big = true)
            }
            PickerField(
                "Customer (needed for debt or warranty)",
                customers.firstOrNull { it.id == cart.customerId }?.name ?: "Walk-in customer",
                listOf("Walk-in customer") + customers.map { it.name },
                onPick = { picked -> cart.customerId = customers.firstOrNull { it.name == picked }?.id },
            )
            TextButton(onClick = { newCustomer = true }) { Text("+ Add customer", color = Indigo, fontWeight = FontWeight.SemiBold) }
            GradientButton(
                "Take payment ${Money.format(total, currency)}",
                { if (discountMinor > subtotal) message = "The discount is more than the bill." else onPay() },
                Modifier.fillMaxWidth(),
            )
        }
    }

    pickPhoneFor?.let { p ->
        val units by repo.observeUnits(p.id).collectAsState(initial = emptyList())
        val chosen = cart.lines.mapNotNull { it.unitId }.toSet()
        val available = units.filter { it.status == "IN_STOCK" && it.id !in chosen }
        EditDialog("Which phone? ${p.name}", false, { pickPhoneFor = null }, {}) {
            if (available.isEmpty()) Text("No more phones of this model in stock.", color = Muted)
            available.forEach { u ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { addProduct(p, u.id, u.imei1); pickPhoneFor = null }) {
                    Text("IMEI ${u.imei1}", fontWeight = FontWeight.SemiBold)
                    if (u.imei2.isNotBlank()) Text("IMEI 2 ${u.imei2}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }

    serviceToPrice?.let { s ->
        var price by remember(s) { mutableStateOf(Money.toInput(s.price)) }
        EditDialog("Add ${s.name}", Money.parse(price) != null, { serviceToPrice = null }, {
            cart.lines.add(CartLine(UUID.randomUUID().toString(), "SERVICE", null, s.name, 1, Money.parse(price) ?: s.price, null, "", 0, false))
            serviceToPrice = null
        }) {
            GlassField(price, { price = it }, "Charge (${currency.code})", keyboard = KeyboardType.Decimal)
        }
    }

    editKey?.let { key ->
        val line = cart.lines.firstOrNull { it.key == key }
        if (line == null) editKey = null else {
            var price by remember(key) { mutableStateOf(Money.toInput(line.unitPrice)) }
            var qty by remember(key) { mutableStateOf(line.qty.toString()) }
            EditDialog(line.name, Money.parse(price) != null, { editKey = null }, {
                val idx = cart.lines.indexOfFirst { it.key == key }
                val available = line.productId?.let { stock[it]?.qty } ?: Int.MAX_VALUE
                val newQty = if (line.tracksImei || line.kind == "SERVICE") 1 else (qty.toIntOrNull() ?: 1).coerceIn(1, maxOf(1, available))
                if (idx >= 0) cart.lines[idx] = line.copy(unitPrice = Money.parse(price) ?: line.unitPrice, qty = newQty)
                editKey = null
            }) {
                GlassField(price, { price = it }, "Price each (${currency.code})", keyboard = KeyboardType.Decimal)
                if (!line.tracksImei && line.kind == "PRODUCT") GlassField(qty, { qty = it }, "Quantity", keyboard = KeyboardType.Number)
                TextButton(onClick = { cart.lines.removeAll { it.key == key }; editKey = null }) { Text("Remove from cart", color = Bad) }
            }
        }
    }

    if (newCustomer) QuickCustomerDialog(repo, onClose = { newCustomer = false }) { id -> cart.customerId = id }
}

@Composable
private fun TotalRow(label: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = if (big) Ink else Muted, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = if (big) 20.sp else 15.sp)
    }
}

@Composable
fun QuickCustomerDialog(repo: Repository, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    EditDialog("New customer", name.isNotBlank(), onClose, {
        scope.launch { onSaved(repo.saveContact(null, CUSTOMER, name, phone, "", "", 0L)); onClose() }
    }) {
        GlassField(name, { name = it }, "Name *")
        GlassField(phone, { phone = it }, "Phone / WhatsApp", keyboard = KeyboardType.Phone)
    }
}
