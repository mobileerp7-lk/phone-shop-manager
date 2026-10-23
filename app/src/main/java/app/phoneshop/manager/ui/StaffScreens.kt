package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.Staff
import app.phoneshop.manager.util.BillPdf
import kotlinx.coroutines.launch

/** Choose who you are and type your PIN. Shown when the shop has more than one person or a PIN is set. */
@Composable
fun LoginScreen(repo: Repository, staff: List<Staff>, onSignedIn: (Staff) -> Unit) {
    var chosen by remember { mutableStateOf<Staff?>(staff.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val current = chosen ?: staff.firstOrNull()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Who is using the app?", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Pick your name and type your PIN.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(staff) { s -> GlassChip("${s.name} · ${roleLabel(s.role)}", s.id == current?.id) { chosen = s; pin = ""; error = null } }
        }
        if (current != null && current.pinHash.isNotEmpty()) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (current != null && current.pinHash.isNotEmpty() &&
            app.phoneshop.manager.util.Prefs.fingerprintStaff(ctx) == current.id &&
            app.phoneshop.manager.util.Fingerprint.available(ctx)
        ) {
            GlassButton("Use fingerprint", {
                app.phoneshop.manager.util.Fingerprint.ask(ctx, "Open the shop", { onSignedIn(current) }) { error = it }
            }, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        GradientButton("Open the shop", {
            val s = current
            if (s == null) error = "Choose a name."
            else if (!repo.verifyPin(s, pin)) error = "Wrong PIN. Try again."
            else onSignedIn(s)
        }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** More → Staff and activity log. Only the owner can change people. */
@Composable
fun StaffScreen(repo: Repository, onBack: () -> Unit) {
    val staff by repo.staff.collectAsState(initial = emptyList())
    val log by repo.activityLog.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Staff?>(null) }
    var adding by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf<String?>(null) }
    val canEdit = Session.isOwner

    ScreenScaffold(
        "Staff and activity log",
        onBack,
        bottom = if (canEdit) ({ GradientButton("+ Add staff", { adding = true }, Modifier.fillMaxWidth()) }) else null,
    ) {
        SectionLabel("People")
        staff.forEach { s ->
            GlassCard(Modifier.fillMaxWidth(), onClick = if (canEdit) ({ editing = s }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold)
                        Text(roleLabel(s.role) + if (s.pinHash.isNotEmpty()) " · PIN set" else " · no PIN", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text("Owner: everything. Manager: everything except changing staff and shop settings. Cashier: selling and repairs only, no buying prices, profit or money screens.", color = Muted, fontSize = 12.sp)
        SectionLabel("Activity log")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GlassChip("Everyone", who == null) { who = null } }
            items(staff) { s -> GlassChip(s.name, who == s.name) { who = s.name } }
        }
        val shown = log.filter { who == null || it.who == who }
        if (shown.isEmpty()) EmptyCard("Nothing yet", "Sales, cancellations, payments and changes are listed here with who did them.")
        shown.forEach { l ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text("${l.who} ${l.text}", fontSize = 14.sp)
                Text(BillPdf.dateText(l.createdAt), color = Muted, fontSize = 11.sp)
            }
        }
        Text("Nothing is ever secretly deleted. A cancelled bill stays in the log with who cancelled it and why.", color = Muted, fontSize = 12.sp)
    }

    if (adding || editing != null) {
        val existing = editing
        var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var role by remember(existing) { mutableStateOf(existing?.role ?: "CASHIER") }
        var pin by remember(existing) { mutableStateOf("") }
        val close = { adding = false; editing = null }
        EditDialog(if (existing == null) "New staff" else "Edit staff", name.isNotBlank() && (pin.isEmpty() || pin.length >= 4), close, {
            scope.launch { repo.saveStaff(existing, name, role, if (pin.isEmpty()) null else pin) }
            close()
        }) {
            GlassField(name, { name = it }, "Name")
            if (existing?.id != "staff_owner") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("MANAGER", "CASHIER")) { r -> GlassChip(roleLabel(r), role == r) { role = r } }
                }
            }
            GlassField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, if (existing == null) "PIN (4 to 8 digits)" else "New PIN (leave empty to keep)", keyboard = KeyboardType.NumberPassword)
            if (existing != null && existing.pinHash.isNotEmpty()) {
                TextButton(onClick = { scope.launch { repo.saveStaff(existing, name, role, "") }; close() }) { Text("Remove PIN", color = Bad) }
            }
            if (existing != null && existing.id != "staff_owner") {
                TextButton(onClick = { scope.launch { repo.removeStaff(existing) }; close() }) { Text("Remove this person", color = Bad) }
            }
        }
    }
}
