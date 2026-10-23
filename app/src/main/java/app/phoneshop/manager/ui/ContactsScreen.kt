package app.phoneshop.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Contact
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

const val CUSTOMER = "CUSTOMER"
const val SUPPLIER = "SUPPLIER"

private fun kindLabel(kind: String) = if (kind == SUPPLIER) "supplier" else "customer"

/** Customers and suppliers in one list with two tabs, as in the wireframe. */
@Composable
fun ContactsScreen(
    repo: Repository,
    settings: ShopSettings,
    startKind: String,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    var kind by rememberSaveable { mutableStateOf(startKind) }
    var query by rememberSaveable { mutableStateOf("") }
    var oweOnly by rememberSaveable { mutableStateOf(false) }

    val ofKind = contacts.filter { it.kind == kind }
    val shown = ofKind.filter {
        (!oweOnly || it.openingBalance > 0) &&
            (query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true))
    }
    val totalOwed = ofKind.sumOf { it.openingBalance.coerceAtLeast(0) }
    val owingCount = ofKind.count { it.openingBalance > 0 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar("Customers and suppliers", onBack = onBack)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Customers", kind == CUSTOMER) { kind = CUSTOMER; oweOnly = false }
                    GlassChip("Suppliers", kind == SUPPLIER) { kind = SUPPLIER; oweOnly = false }
                }
                GlassField(query, { query = it }, "Search name or phone")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("All", !oweOnly) { oweOnly = false }
                    GlassChip(if (kind == CUSTOMER) "Owe money ($owingCount)" else "You owe ($owingCount)", oweOnly) { oweOnly = !oweOnly }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(if (kind == CUSTOMER) "Customers owe you" else "You owe suppliers", color = Muted, fontSize = 13.sp)
                    Text(Money.format(totalOwed, currency), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (totalOwed > 0) Warn else Good)
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    EmptyCard(
                        if (ofKind.isEmpty()) "No ${kindLabel(kind)}s yet" else "Nothing matches",
                        if (ofKind.isEmpty()) "Tap \"Add ${kindLabel(kind)}\" to add the first one." else "Try another word or clear the filter.",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                        GlassCard(Modifier.fillMaxWidth().then(if (i < 8) Modifier.riseIn(i) else Modifier), onClick = { onOpen(c.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(c.name)
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    if (c.phone.isNotBlank()) Text(c.phone, color = Muted, fontSize = 13.sp)
                                }
                                Text(
                                    Money.format(c.openingBalance, currency),
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.openingBalance > 0) Warn else Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
        GradientButton(
            "+ Add ${kindLabel(kind)}",
            { onAdd(kind) },
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun Avatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(Modifier.size(44.dp).background(Indigo.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(initials.ifEmpty { "?" }, color = Indigo, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ContactFormScreen(
    repo: Repository,
    settings: ShopSettings,
    kind: String,
    contactId: String?,
    onBack: () -> Unit,
) {
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<Contact?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(contactId) {
        if (contactId != null) {
            val c = repo.getContact(contactId)
            if (c != null) {
                existing = c; name = c.name; phone = c.phone; address = c.address; note = c.note
                opening = if (c.openingBalance != 0L) Money.toInput(c.openingBalance) else ""
            }
        }
    }
    val label = kindLabel(existing?.kind ?: kind)

    ScreenScaffold(
        if (contactId == null) "Add $label" else "Edit $label",
        onBack,
        bottom = {
            GradientButton("Save", {
                val bal = if (opening.isBlank()) 0L else Money.parse(opening)
                when {
                    name.isBlank() -> error = "Please enter a name."
                    bal == null -> error = "The amount owed is not a number."
                    else -> scope.launch {
                        repo.saveContact(existing, existing?.kind ?: kind, name, phone, address, note, bal)
                        onBack()
                    }
                }
            }, Modifier.fillMaxWidth())
        },
    ) {
        GlassField(name, { name = it }, "Name *")
        GlassField(phone, { phone = it }, "Phone / WhatsApp", keyboard = KeyboardType.Phone)
        GlassField(address, { address = it }, "Address")
        GlassField(note, { note = it }, "Note")
        GlassField(
            opening, { opening = it },
            if ((existing?.kind ?: kind) == CUSTOMER) "Already owes you (${currency.code}), optional" else "You already owe them (${currency.code}), optional",
            keyboard = KeyboardType.Decimal,
        )
        Text("Use this only for money owed before you started using the app.", color = Muted, fontSize = 13.sp)
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
        if (existing != null) {
            GlassButton("Delete $label", { confirmDelete = true }, Modifier.fillMaxWidth(), danger = true)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this $label?",
            "Their past bills stay in your records.",
            "Delete",
            onConfirm = { scope.launch { repo.deleteContact(contactId ?: return@launch); onBack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The profile page: what they owe and, in later steps, their full history. */
@Composable
fun ContactDetailScreen(
    repo: Repository,
    settings: ShopSettings,
    contactId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val contact by repo.observeContact(contactId).collectAsState(initial = null)
    val currency = Locales.currency(settings.currencyCode)
    val c = contact
    ScreenScaffold(
        if (c?.kind == SUPPLIER) "Supplier profile" else "Customer profile",
        onBack,
        actions = { TextButton(onClick = onEdit, enabled = c != null) { Text("Edit", color = Indigo, fontWeight = FontWeight.SemiBold) } },
    ) {
        if (c == null) return@ScreenScaffold
        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(c.name)
                Column {
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp)
                }
            }
            if (c.note.isNotBlank()) Text(c.note, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        GlassCard(Modifier.fillMaxWidth().riseIn(1)) {
            Text(if (c.kind == SUPPLIER) "You owe them" else "Owes you", color = Muted, fontSize = 13.sp)
            Text(
                Money.format(c.openingBalance, currency),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (c.openingBalance > 0) Warn else Good,
            )
        }
        SectionLabel("History")
        EmptyCard("Nothing yet", "Bills, repairs and payments will appear here like a statement once selling is added.")
    }
}
