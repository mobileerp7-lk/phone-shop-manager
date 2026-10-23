package app.phoneshop.manager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Sell", Icons.Filled.ShoppingCart),
    Tab("Stock", Icons.AutoMirrored.Filled.List),
    Tab("Money", Icons.Filled.AccountBox),
    Tab("More", Icons.Filled.Menu),
)

@Composable
fun MainShell(
    repo: Repository,
    settings: ShopSettings,
    onAddProduct: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onNavigate: (String) -> Unit,
    cart: CartState,
    onPay: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(cart.requestTab) {
        cart.requestTab?.let { tab = it; cart.requestTab = null }
    }
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                    }
                },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(repo, settings, onAddProduct, onGoStock = { tab = 2 })
                    2 -> ProductListScreen(repo, settings, onAddProduct, onOpenProduct)
                    1 -> SellScreen(repo, settings, cart, onPay)
                    3 -> MoneyScreen(repo, settings, onNavigate)
                    4 -> MoreScreen { route ->
                        when (route) {
                            "products" -> tab = 2
                            "sell" -> tab = 1
                            "bills" -> onNavigate("bills")
                            "repairs" -> onNavigate("repairs")
                            "po" -> onNavigate("purchases/ORDERS")
                            "purchases" -> onNavigate("purchases/BILLS")
                            "accounts" -> tab = 3
                            "money_entries" -> onNavigate("statement/ALL")
                            "transfers" -> onNavigate("money_entry/TRANSFER")
                            else -> onNavigate(route)
                        }
                    }
                    else -> ComingSoon(tabs[t].label)
                }
            }
        }
        BottomBar(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassBase.copy(alpha = 0.72f))
            .border(1.dp, GlassEdge)
            .navigationBarsPadding()
    ) {
        tabs.forEachIndexed { index, item ->
            val on = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (on) Indigo else Muted)
                Text(
                    item.label,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Indigo else Muted,
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.fillMaxWidth().riseIn()) {
                Text("Coming in a later step", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "This part of the app is planned and designed. We build it one step at a time, starting with products.",
                    color = Muted,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
