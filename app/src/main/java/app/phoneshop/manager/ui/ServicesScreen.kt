package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.Service
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

/** Repairs, software installs and other paid jobs, each with a usual charge. */
@Composable
fun ServicesScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val services by repo.services.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Service?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        "Services and charges",
        onBack,
        bottom = { GradientButton("+ Add service", { adding = true }, Modifier.fillMaxWidth()) },
    ) {
        Text(
            "Jobs you do for a fee. The usual charge is filled in when you sell, and you can change it on each bill.",
            color = Muted,
            fontSize = 13.sp,
        )
        if (services.isEmpty()) {
            EmptyCard("No services yet", "Add things like screen repair, software install or unlocking.")
        }
        services.forEachIndexed { i, s ->
            GlassCard(Modifier.fillMaxWidth().riseIn(minOf(i, 8)), onClick = { editing = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (s.note.isNotBlank()) Text(s.note, color = Muted, fontSize = 13.sp)
                    }
                    Text(Money.format(s.price, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (adding || editing != null) {
        val existing = editing
        var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var price by remember(existing) { mutableStateOf(existing?.let { Money.toInput(it.price) } ?: "") }
        var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
        var confirmDelete by remember { mutableStateOf(false) }
        val close = { adding = false; editing = null }
        EditDialog(
            if (existing == null) "New service" else "Edit service",
            canSave = name.isNotBlank() && Money.parse(price) != null,
            onDismiss = close,
            onSave = {
                scope.launch { repo.saveService(existing, name, Money.parse(price) ?: 0L, note) }
                close()
            },
        ) {
            GlassField(name, { name = it }, "Service name *")
            GlassField(price, { price = it }, "Usual charge (${currency.code}) *", keyboard = KeyboardType.Decimal)
            GlassField(note, { note = it }, "Note (optional)")
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete this service", color = Bad) }
            }
        }
        if (confirmDelete && existing != null) {
            ConfirmDialog(
                "Delete this service?",
                "Past bills keep their record. It will no longer be offered when selling.",
                "Delete",
                onConfirm = { scope.launch { repo.deleteService(existing.id) }; confirmDelete = false; close() },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
