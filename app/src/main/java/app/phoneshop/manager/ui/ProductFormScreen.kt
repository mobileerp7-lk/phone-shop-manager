package app.phoneshop.manager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.CategoryField
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.data.UnitInput
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import app.phoneshop.manager.util.copyPhoto
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun ProductFormScreen(
    repo: Repository,
    settings: ShopSettings,
    productId: String?,
    onBack: () -> Unit,
) {
    val categories by repo.categories.collectAsState(initial = emptyList())
    val allFields by repo.fields.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = productId != null

    var categoryId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var itemCode by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var warranty by remember { mutableStateOf("") }
    var lowStock by remember { mutableStateOf("2") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }

    var buyPrice by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    val imeis = remember { mutableStateListOf(UnitInput("", "")) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Load an existing product when editing.
    LaunchedEffect(productId) {
        if (productId != null) {
            repo.getProduct(productId)?.let { p ->
                categoryId = p.categoryId
                name = p.name
                itemCode = p.itemCode
                barcode = p.barcode
                sell = Money.toInput(p.sellPrice)
                minPrice = Money.toInput(p.minPrice)
                warranty = if (p.warrantyMonths > 0) p.warrantyMonths.toString() else ""
                lowStock = p.lowStockLevel.toString()
                photoPath = p.photoPath
                val json = JSONObject(p.attributesJson)
                json.keys().forEach { key -> values[key] = json.optString(key) }
            }
        }
    }
    // A new product is offered the next free item code, which the owner may change.
    LaunchedEffect(productId) {
        if (productId == null && itemCode.isEmpty()) itemCode = repo.suggestItemCode()
    }
    // A new product starts on the first category.
    LaunchedEffect(categories) {
        if (categoryId == null && categories.isNotEmpty()) categoryId = categories.first().id
    }

    val category = categories.firstOrNull { it.id == categoryId }
    val fields = allFields.filter { it.categoryId == categoryId }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { copyPhoto(context, uri) }
                if (saved != null) photoPath = saved else error = "Could not use that photo."
            }
        }
    }

    fun save() {
        val cat = category ?: return
        if (saving) return
        // Required questions for this category.
        for (f in fields) {
            if (f.required && values[f.id].isNullOrBlank()) {
                error = "Please fill in: ${f.label}"
                return
            }
        }
        val finalName = name.trim().ifEmpty { autoName(fields, values) }
        if (finalName.isEmpty()) {
            error = "Please enter a product name."
            return
        }
        val sellMinor = Money.parse(sell)
        if (sellMinor == null || sellMinor <= 0) {
            error = "Please enter the selling price."
            return
        }
        val minMinor = if (minPrice.isBlank()) 0L else (Money.parse(minPrice) ?: run {
            error = "The lowest price is not a number."
            return
        })
        val costMinor = if (buyPrice.isBlank()) 0L else (Money.parse(buyPrice) ?: run {
            error = "The buying price is not a number."
            return
        })
        val json = JSONObject()
        fields.forEach { f -> values[f.id]?.let { if (it.isNotBlank()) json.put(f.id, it) } }
        saving = true
        error = null
        scope.launch {
            val result = repo.saveProduct(
                existingId = productId,
                categoryId = cat.id,
                name = finalName,
                itemCode = itemCode.trim(),
                barcode = barcode.trim(),
                sellPrice = sellMinor,
                minPrice = minMinor,
                warrantyMonths = warranty.toIntOrNull() ?: 0,
                lowStockLevel = lowStock.toIntOrNull() ?: 0,
                photoPath = photoPath,
                attributesJson = json.toString(),
                tracksImei = cat.tracksImei,
                openingUnits = imeis.toList(),
                openingQty = qty.toIntOrNull() ?: 0,
                openingCost = costMinor,
            )
            saving = false
            if (result == null) onBack() else error = result
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        GlassTopBar(if (editing) "Edit product" else "Add product", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(14.dp)
                val path = photoPath
                if (path != null && File(path).exists()) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Product photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(shape),
                    )
                } else {
                    Box(
                        Modifier.size(84.dp).background(Indigo.copy(alpha = 0.1f), shape),
                        contentAlignment = Alignment.Center,
                    ) { Text("No photo", color = Muted, fontSize = 12.sp) }
                }
                GlassButton(
                    if (path == null) "Add photo" else "Change photo",
                    {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }

            if (!editing) {
                SectionLabel("Category (decides the questions below)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { c ->
                        GlassChip(c.name, c.id == categoryId) {
                            if (c.id != categoryId) {
                                categoryId = c.id
                                values.clear()
                            }
                        }
                    }
                }
            } else {
                Text("Category: ${category?.name ?: ""}", color = Muted)
            }

            SectionLabel("${category?.name ?: "Product"} details")
            for (f in fields) FieldInput(f, values[f.id] ?: "") { values[f.id] = it }
            GlassField(name, { name = it }, "Product name (optional: made from the details above)")
            GlassField(itemCode, { itemCode = it.uppercase().filter { c -> !c.isWhitespace() } }, "Item code (made for you, you can change it)")
            GlassField(barcode, { barcode = it }, "Barcode / SKU (optional)")
            val scanContext = androidx.compose.ui.platform.LocalContext.current
            GlassButton("Scan barcode with camera", {
                app.phoneshop.manager.util.BarcodeScan.scan(scanContext, { barcode = it })
            }, Modifier.fillMaxWidth())

            SectionLabel("Prices (${currency.code})")
            GlassField(sell, { sell = it }, "Selling price *", keyboard = KeyboardType.Decimal)
            GlassField(minPrice, { minPrice = it }, "Lowest price staff may sell at", keyboard = KeyboardType.Decimal)
            if (category?.hasWarranty == true) {
                GlassField(warranty, { warranty = it }, "Warranty (months)", keyboard = KeyboardType.Number)
            }
            GlassField(lowStock, { lowStock = it }, "Warn me when stock is this many or fewer", keyboard = KeyboardType.Number)

            if (!editing) {
                SectionLabel("Opening stock (what you have now)")
                GlassField(buyPrice, { buyPrice = it }, "Buying price each (${currency.code})", keyboard = KeyboardType.Decimal)
                if (category?.tracksImei == true) {
                    Text(
                        "Each phone is tracked by IMEI (15 digits). Dial *#06# on a phone to see it.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    imeis.forEachIndexed { i, unit ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Phone ${i + 1}", fontWeight = FontWeight.SemiBold)
                            Column(
                                Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GlassField(unit.imei1, { imeis[i] = unit.copy(imei1 = it) }, "IMEI 1", keyboard = KeyboardType.Number)
                                GlassField(unit.imei2, { imeis[i] = unit.copy(imei2 = it) }, "IMEI 2 (optional)", keyboard = KeyboardType.Number)
                                if (imeis.size > 1) {
                                    GlassButton("Remove this phone", { imeis.removeAt(i) }, Modifier.fillMaxWidth(), danger = true)
                                }
                            }
                        }
                    }
                    GlassButton("+ Add another phone", { imeis.add(UnitInput("", "")) }, Modifier.fillMaxWidth())
                } else {
                    GlassField(qty, { qty = it }, "How many do you have?", keyboard = KeyboardType.Number)
                }
            } else {
                Text(
                    "To add more stock, open the product and tap \"Add stock\".",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }

            error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassBase.copy(alpha = 0.7f))
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            GradientButton(if (saving) "Saving..." else "Save product", { save() }, Modifier.fillMaxWidth(), enabled = !saving)
        }
    }
}

private fun autoName(fields: List<CategoryField>, values: Map<String, String>): String {
    val order = listOf("Brand", "Model", "Storage", "Colour", "Type", "Part", "Compatible models")
    return order.mapNotNull { label ->
        fields.firstOrNull { it.label == label }?.let { values[it.id] }?.takeIf { it.isNotBlank() }
    }.joinToString(" ")
}

@Composable
private fun FieldInput(field: CategoryField, value: String, onChange: (String) -> Unit) {
    val label = field.label + if (field.required) " *" else ""
    when (field.type) {
        "CHOICE" -> PickerField(label, value, field.options.split("|").filter { it.isNotBlank() }, onChange)
        "YESNO" -> GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.label, Modifier.weight(1f))
                Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
            }
        }
        "NUMBER" -> GlassField(value, onChange, label, keyboard = KeyboardType.Number)
        else -> GlassField(value, onChange, label)
    }
}
