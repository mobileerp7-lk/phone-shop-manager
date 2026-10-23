package app.phoneshop.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Category
import app.phoneshop.manager.data.Product
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.data.StockInfo
import app.phoneshop.manager.util.CurrencyInfo
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ProductListScreen(
    repo: Repository,
    settings: ShopSettings,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val products by repo.products.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val stock by repo.stock.collectAsState(initial = emptyMap())
    val currency = Locales.currency(settings.currencyCode)

    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var lowOnly by rememberSaveable { mutableStateOf(false) }

    // Searching 4 or more digits also looks inside IMEI numbers.
    val imeiMatches by produceState(emptyList<String>(), query) {
        value = if (query.length >= 4) repo.productIdsByImei(query.trim()) else emptyList()
    }

    val q = query.trim()
    val shown = products.filter { p ->
        (categoryFilter == null || p.categoryId == categoryFilter) &&
            (!lowOnly || (stock[p.id]?.qty ?: 0) <= p.lowStockLevel) &&
            (
                q.isEmpty() ||
                    p.name.contains(q, ignoreCase = true) ||
                    p.itemCode.contains(q, ignoreCase = true) ||
                    p.barcode.contains(q, ignoreCase = true) ||
                    p.attributesJson.contains(q, ignoreCase = true) ||
                    p.id in imeiMatches
                )
    }
    val categoryById = categories.associateBy { it.id }
    val stockValue = stock.values.sumOf { it.totalCost }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stock", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                GlassField(query, { query = it }, "Search name, item code, barcode or IMEI")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GlassChip("All", categoryFilter == null && !lowOnly) { categoryFilter = null; lowOnly = false } }
                    items(categories) { c ->
                        GlassChip(c.name, categoryFilter == c.id) {
                            categoryFilter = if (categoryFilter == c.id) null else c.id
                        }
                    }
                    item { GlassChip("Low stock", lowOnly) { lowOnly = !lowOnly } }
                }
                Text(
                    "Stock value at cost: ${Money.format(stockValue, currency)}",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    GlassCard(Modifier.fillMaxWidth().riseIn()) {
                        Text(
                            if (products.isEmpty()) "No products yet" else "Nothing matches your search",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            if (products.isEmpty()) "Tap \"Add product\" to add your first phone or accessory."
                            else "Try a different word or clear the filters.",
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(shown, key = { _, p -> p.id }) { index, p ->
                        ProductCard(
                            p,
                            categoryById[p.categoryId],
                            stock[p.id],
                            currency,
                            (if (index < 8) Modifier.riseIn(index) else Modifier),
                        ) { onOpen(p.id) }
                    }
                }
            }
        }
        GradientButton(
            "+ Add product",
            onAdd,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ProductCard(
    product: Product,
    category: Category?,
    stock: StockInfo?,
    currency: CurrencyInfo,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val qty = stock?.qty ?: 0
    val low = qty <= product.lowStockLevel
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProductPhoto(product, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${product.itemCode} · ${category?.name ?: "Product"} · $qty in stock", color = Muted, fontSize = 13.sp)
                if (low) StatusPill(if (qty == 0) "Out of stock" else "Low stock", Color(0xFFFFEDD5), Warn)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(product.sellPrice, currency), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (stock != null) {
                    val cost = if (stock.minCost == stock.maxCost) {
                        Money.format(stock.minCost, currency)
                    } else {
                        "${Money.format(stock.minCost, currency)}+"
                    }
                    Text("cost $cost", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ProductPhoto(product: Product, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val path = product.photoPath
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier.background(Indigo.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                product.name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Indigo,
            )
        }
    }
}
