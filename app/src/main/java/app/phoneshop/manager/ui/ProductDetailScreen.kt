package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Product
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.data.UnitInput
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun ProductDetailScreen(
    repo: Repository,
    settings: ShopSettings,
    productId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val product by repo.observeProduct(productId).collectAsState(initial = null)
    val units by repo.observeUnits(productId).collectAsState(initial = emptyList())
    val batches by repo.observeBatches(productId).collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val fields by repo.fields.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val p = product
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            p?.name ?: "Product",
            onBack = onBack,
            actions = {
                TextButton(onClick = onEdit, enabled = p != null) { Text("Edit", color = Indigo, fontWeight = FontWeight.SemiBold) }
            },
        )
        if (p == null) return@Column
        val category = categories.firstOrNull { it.id == p.categoryId }
        val tracksImei = category?.tracksImei == true
        val inStock = units.filter { it.status == "IN_STOCK" }
        val batchesLeft = batches.filter { it.remaining > 0 }
        val qty = if (tracksImei) inStock.size else batchesLeft.sumOf { it.remaining }
        val costs = if (tracksImei) inStock.map { it.unitCost } else batchesLeft.map { it.unitCost }
        val value = if (tracksImei) inStock.sumOf { it.unitCost } else batchesLeft.sumOf { it.unitCost * it.remaining }
        val attrs = remember(p.attributesJson, fields) { readAttributes(p, fields.filter { it.categoryId == p.categoryId }.map { it.id to it.label }) }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(p, Modifier.size(84.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(category?.name ?: "Product", color = Muted, fontSize = 13.sp)
                        Text(
                            "Sells at ${Money.format(p.sellPrice, currency)}",
                            fontWeight = FontWeight.SemiBold,
                            color = Indigo,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (p.minPrice > 0) Text("Lowest allowed ${Money.format(p.minPrice, currency)}", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("In stock", qty.toString(), Modifier.weight(1f).riseIn(1), if (qty <= p.lowStockLevel) Warn else Good)
                val costText = when {
                    costs.isEmpty() -> "-"
                    costs.min() == costs.max() -> Money.format(costs.min(), currency)
                    else -> "${Money.format(costs.min(), currency)} - ${Money.format(costs.max(), currency)}"
                }
                if (Session.canSeeCost) Stat("Buying cost", costText, Modifier.weight(1f).riseIn(2))
            }
            if (Session.canSeeCost) Stat("Stock value at cost", Money.format(value, currency), Modifier.fillMaxWidth().riseIn(3))

            run {
                SectionLabel("Details")
                GlassCard(Modifier.fillMaxWidth().riseIn(4)) {
                    InfoRow("Item code", p.itemCode)
                    attrs.forEach { (k, v) -> InfoRow(k, v) }
                    if (p.barcode.isNotBlank()) InfoRow("Barcode", p.barcode)
                    if (p.warrantyMonths > 0) InfoRow("Warranty", "${p.warrantyMonths} months")
                    InfoRow("Low stock alert at", p.lowStockLevel.toString())
                }
            }

            if (tracksImei) {
                SectionLabel("Phones (each with its own cost)")
                if (units.isEmpty()) Text("No phones added yet.", color = Muted)
                units.forEach { u ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("IMEI ${u.imei1}", fontWeight = FontWeight.SemiBold)
                                if (u.imei2.isNotBlank()) Text("IMEI 2 ${u.imei2}", color = Muted, fontSize = 13.sp)
                                Text("Added ${date(u.createdAt)}", color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (Session.canSeeCost) Text(Money.format(u.unitCost, currency), fontWeight = FontWeight.Bold)
                                if (u.status == "IN_STOCK") StatusPill("In stock", Color(0xFFDCFCE7), Good)
                                else StatusPill("Sold", Color(0xFFE4E4E7), Muted)
                            }
                        }
                    }
                }
            } else {
                SectionLabel("Purchase batches (each with its own cost)")
                if (batches.isEmpty()) Text("No stock added yet.", color = Muted)
                batches.forEach { b ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${b.remaining} left of ${b.qty}", fontWeight = FontWeight.SemiBold)
                                Text("Bought ${date(b.createdAt)}", color = Muted, fontSize = 12.sp)
                            }
                            if (Session.canSeeCost) Text("${Money.format(b.unitCost, currency)} each", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            GradientButton("+ Add stock", { showAdd = true }, Modifier.fillMaxWidth())
            GlassButton("Delete product", { showDelete = true }, Modifier.fillMaxWidth(), danger = true)
            Column(Modifier.navigationBarsPadding()) {}
        }

        if (showAdd) {
            AddStockDialog(tracksImei, currency, onDismiss = { showAdd = false }) { list, q, cost ->
                repo.addStock(p.id, tracksImei, list, q, cost)
            }
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Delete this product?") },
                text = { Text("It will be removed from your stock list. Past sales are not affected.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDelete = false
                        scope.launch { repo.deleteProduct(p.id); onBack() }
                    }) { Text("Delete", color = Bad) }
                },
                dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep it") } },
            )
        }
    }
}

@Composable
private fun AddStockDialog(
    tracksImei: Boolean,
    currency: CurrencyInfo,
    onDismiss: () -> Unit,
    onSave: suspend (List<UnitInput>, Int, Long) -> String?,
) {
    val scope = rememberCoroutineScope()
    val imeis = remember { mutableStateListOf(UnitInput("", "")) }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stock") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the buying price for this lot. Older stock keeps its own price.", color = Muted, fontSize = 13.sp)
                GlassField(cost, { cost = it }, "Buying price each (${currency.code})", keyboard = KeyboardType.Decimal)
                if (tracksImei) {
                    imeis.forEachIndexed { i, u ->
                        GlassField(u.imei1, { imeis[i] = u.copy(imei1 = it) }, "IMEI 1", keyboard = KeyboardType.Number)
                        GlassField(u.imei2, { imeis[i] = u.copy(imei2 = it) }, "IMEI 2 (optional)", keyboard = KeyboardType.Number)
                    }
                    TextButton(onClick = { imeis.add(UnitInput("", "")) }) { Text("+ Another phone") }
                } else {
                    GlassField(qty, { qty = it }, "Quantity", keyboard = KeyboardType.Number)
                }
                error?.let { Text(it, color = Bad, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Money.parse(cost)
                if (c == null || c < 0) {
                    error = "Enter the buying price."
                } else {
                    scope.launch {
                        val result = onSave(imeis.toList(), qty.toIntOrNull() ?: 0, c)
                        if (result == null) onDismiss() else error = result
                    }
                }
            }) { Text("Save", color = Indigo, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, accent: Color = Ink) {
    GlassCard(modifier) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

private fun readAttributes(p: Product, fields: List<Pair<String, String>>): List<Pair<String, String>> {
    val json = runCatching { JSONObject(p.attributesJson) }.getOrNull() ?: return emptyList()
    return fields.mapNotNull { (id, label) ->
        val v = json.optString(id, "")
        if (v.isBlank()) null else label to v
    }
}

private fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
