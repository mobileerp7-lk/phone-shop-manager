package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.PaymentInput
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private class PayRow(val accountId: String, amount: String) {
    var amount by mutableStateOf(amount)
}

/** Parses a promise date typed as YYYY-MM-DD. Blank means no promise. */
fun parseDay(text: String): Long? = runCatching {
    LocalDate.parse(text.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

/** Splits the bill across cash, bank, wallet and credit. Anything unpaid becomes the customer's debt. */
@Composable
fun PaymentScreen(
    repo: Repository,
    settings: ShopSettings,
    cart: CartState,
    onBack: () -> Unit,
    onDone: (String) -> Unit,
) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val balances by repo.contactBalances.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()

    val discount = Money.parse(cart.discount) ?: 0L
    val total = (cart.subtotal - discount).coerceAtLeast(0)
    val rows = remember { mutableStateListOf<PayRow>() }
    var promise by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }

    if (rows.isEmpty() && views.isNotEmpty()) rows.add(PayRow(views.first().account.id, Money.toInput(total)))

    val paid = rows.sumOf { Money.parse(it.amount) ?: 0L }
    val left = (total - paid).coerceAtLeast(0)
    val change = (paid - total).coerceAtLeast(0)
    val customers = contacts.filter { it.kind == CUSTOMER }
    val customer = customers.firstOrNull { it.id == cart.customerId }

    fun finish() {
        if (saving) return
        var payments = rows.mapNotNull { r -> Money.parse(r.amount)?.takeIf { it > 0 }?.let { PaymentInput(r.accountId, it) } }
        if (change > 0) {
            // Overpaid: only possible in cash, and the customer gets the change back.
            val cashId = views.firstOrNull { v -> v.account.type == "CASH" && payments.any { it.accountId == v.account.id && it.amount >= change } }?.account?.id
            if (cashId == null) { error = "The payments are more than the bill. Change can only be given from cash."; return }
            var taken = false
            payments = payments.map { if (!taken && it.accountId == cashId && it.amount >= change) { taken = true; it.copy(amount = it.amount - change) } else it }
        }
        val promiseMs = if (promise.isBlank()) null else parseDay(promise)
        if (promise.isNotBlank() && promiseMs == null) { error = "Write the date like 2026-11-06."; return }
        saving = true
        scope.launch {
            val result = repo.completeSale(cart.lines.toList(), discount, cart.customerId, payments, promiseMs)
            saving = false
            if (result.saleId != null) { cart.clear(); onDone(result.saleId) } else error = result.error
        }
    }

    ScreenScaffold(
        "Take payment",
        onBack,
        bottom = { GradientButton(if (saving) "Saving..." else "Finish sale", { finish() }, Modifier.fillMaxWidth(), enabled = !saving) },
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Bill total", color = Muted, fontSize = 13.sp)
            Text(Money.format(total, currency), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        PickerField(
            "Customer",
            customer?.name ?: "Walk-in customer",
            listOf("Walk-in customer") + customers.map { it.name },
            onPick = { picked -> cart.customerId = customers.firstOrNull { it.name == picked }?.id },
        )
        Row {
            TextButton(onClick = { newCustomer = true }) { Text("+ Add customer", color = Indigo, fontWeight = FontWeight.SemiBold) }
            if (customer != null) {
                Text(
                    "owes ${Money.format(balances[customer.id] ?: 0L, currency)} now",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        SectionLabel("How is the customer paying?")
        rows.forEachIndexed { i, r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Paid into", color = Muted, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(views) { v ->
                        GlassChip(v.account.name, v.account.id == r.accountId) {
                            rows[i] = PayRow(v.account.id, r.amount)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassField(r.amount, { r.amount = it }, "Amount (${currency.code})", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    if (rows.size > 1) TextButton(onClick = { rows.removeAt(i) }) { Text("×", color = Bad, fontSize = 22.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("+ Another method", {
                val used = rows.map { it.accountId }.toSet()
                val next = views.firstOrNull { it.account.id !in used } ?: views.firstOrNull()
                if (next != null) rows.add(PayRow(next.account.id, ""))
            }, Modifier.weight(1f))
            GlassButton("Pay full in cash", {
                val cash = views.firstOrNull { it.account.type == "CASH" } ?: views.firstOrNull()
                rows.clear()
                if (cash != null) rows.add(PayRow(cash.account.id, Money.toInput(total)))
            }, Modifier.weight(1f))
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SumRow("Paid now", Money.format(paid.coerceAtMost(total), currency))
            if (change > 0) SumRow("Change to give back", Money.format(change, currency))
            SumRow("Left to pay (debt)", Money.format(left, currency), highlight = left > 0)
            if (left > 0) {
                Text(
                    if (customer != null) "This ${Money.format(left, currency)} will be added to ${customer.name}'s balance."
                    else "Choose a customer above so the debt can be recorded.",
                    color = if (customer != null) Muted else Bad, fontSize = 13.sp,
                )
            }
        }
        if (left > 0) GlassField(promise, { promise = it }, "Promise to pay by (optional, like 2026-11-06)")
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
    if (newCustomer) QuickCustomerDialog(repo, onClose = { newCustomer = false }) { id -> cart.customerId = id }
}

@Composable
private fun SumRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = FontWeight.ExtraBold, color = if (highlight) Warn else Ink)
    }
}
