package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.BillPdf
import app.phoneshop.manager.util.BillView
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

/** The finished bill, with share/print/save as PDF and a way to cancel it. */
@Composable
fun BillScreen(
    repo: Repository,
    settings: ShopSettings,
    saleId: String,
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    onReturn: () -> Unit,
) {
    val sale by repo.observeSale(saleId).collectAsState(initial = null)
    val lines by repo.observeSaleLines(saleId).collectAsState(initial = emptyList())
    val entries by repo.entries.collectAsState(initial = emptyList())
    val accounts by repo.accounts.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cancelling by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val s = sale
    ScreenScaffold("Bill", onBack) {
        if (s == null) return@ScreenScaffold
        val names = accounts.associate { it.id to it.name }
        val payments = entries.filter { it.refId == saleId && it.kind == "SALE" }.map { (names[it.accountId] ?: "Account") to it.amount }
        val customerName = contacts.firstOrNull { it.id == s.customerId }?.name ?: "Walk-in"
        val cancelled = s.status == "CANCELLED"

        if (cancelled) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Cancelled", color = Bad, fontWeight = FontWeight.Bold)
                Text("Reason: ${s.cancelReason}. Stock and money were put back.", color = Muted, fontSize = 13.sp)
            }
        } else {
            GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
                Text("Done", color = Good, fontWeight = FontWeight.Bold)
                Text("Sale saved. Stock and money accounts updated.", color = Muted, fontSize = 13.sp)
            }
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(1)) {
            Text(settings.shopName.ifBlank { "Your shop" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(listOf(settings.address, settings.phone).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
            if (settings.taxEnabled && settings.taxNumber.isNotBlank()) Text("Tax no. ${settings.taxNumber}", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Bill #${s.billNo}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(BillPdf.dateText(s.createdAt), color = Muted, fontSize = 12.sp)
            }
            Text("Customer: $customerName · Staff: ${s.staffName}", color = Muted, fontSize = 12.sp)
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("${l.name} ×${l.qty}", Modifier.weight(1f))
                    Text(Money.format(l.lineTotal, currency), fontWeight = FontWeight.SemiBold)
                }
                val extra = buildList {
                    if (l.imei.isNotBlank()) add("IMEI ${l.imei}")
                    if (l.warrantyMonths > 0) add("Warranty till ${BillPdf.warrantyUntil(s.createdAt, l.warrantyMonths)}")
                }.joinToString(" · ")
                if (extra.isNotBlank()) Text(extra, color = Muted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) { }
            if (s.discount > 0) BillRow("Discount", "−" + Money.format(s.discount, currency))
            if (s.taxAmount > 0) BillRow("Includes tax", Money.format(s.taxAmount, currency))
            BillRow("TOTAL", Money.format(s.total, currency), bold = true)
            payments.forEach { (name, amount) -> BillRow("Paid: $name", Money.format(amount, currency)) }
            if (s.due > 0) BillRow("Balance due", Money.format(s.due, currency), bold = true, color = Warn)
        }
        GradientButton("Share / print / save PDF", {
            val file = BillPdf.create(context, BillView(settings, s, lines, customerName, payments), currency)
            BillPdf.share(context, file, s.billNo)
        }, Modifier.fillMaxWidth())
        Text("The share sheet lets you send the bill on WhatsApp, print it, or save it as a PDF.", color = Muted, fontSize = 12.sp)
        GlassButton("Start new sale", onNewSale, Modifier.fillMaxWidth())
        if (!cancelled) GlassButton("Customer returns items", onReturn, Modifier.fillMaxWidth())
        if (!cancelled && Session.canCancel) GlassButton("Cancel this bill", { cancelling = true; reason = "" }, Modifier.fillMaxWidth(), danger = true)
        error?.let { Text(it, color = Bad) }
    }
    if (cancelling) {
        EditDialog("Cancel bill #${s?.billNo}?", reason.isNotBlank(), { cancelling = false }, {
            scope.launch { error = repo.cancelSale(saleId, reason); cancelling = false }
        }) {
            Text("Stock and money are put back. The bill stays in your records marked Cancelled.", color = Muted, fontSize = 13.sp)
            GlassField(reason, { reason = it }, "Reason (required)")
        }
    }
}

@Composable
private fun BillRow(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, color = color)
    }
}

/** More → Bills and returns: every bill, newest first. */
@Composable
fun BillsListScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit, onOpen: (String) -> Unit, onTradeIn: () -> Unit) {
    val sales by repo.sales.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    ScreenScaffold("Bills and returns", onBack) {
        GlassButton("Trade-in: buy a used phone", onTradeIn, Modifier.fillMaxWidth())
        if (sales.isEmpty()) EmptyCard("No bills yet", "Bills you make in Sell appear here.")
        sales.forEach { s ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(s.id) }) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("Bill #${s.billNo}", fontWeight = FontWeight.SemiBold)
                        Text(
                            (contacts.firstOrNull { it.id == s.customerId }?.name ?: "Walk-in") + " · " + BillPdf.dateText(s.createdAt),
                            color = Muted, fontSize = 12.sp,
                        )
                        if (s.status == "CANCELLED") StatusPill("Cancelled", Color(0xFFFEE2E2), Color(0xFF991B1B))
                        else if (s.due > 0) StatusPill("Owes ${Money.format(s.due, currency)}", Color(0xFFFFEDD5), Color(0xFF9A3412))
                    }
                    Text(Money.format(s.total, currency), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
