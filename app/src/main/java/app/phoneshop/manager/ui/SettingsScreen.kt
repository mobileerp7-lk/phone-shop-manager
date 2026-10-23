package app.phoneshop.manager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import app.phoneshop.manager.util.Backup
import app.phoneshop.manager.util.BillPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import kotlinx.coroutines.launch

private enum class Editing { NONE, SHOP, REGION, TAX, CODE, PIN }

@Composable
fun SettingsScreen(
    repo: Repository,
    settings: ShopSettings,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val services by repo.services.collectAsState(initial = emptyList())
    val staff by repo.staff.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(Editing.NONE) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    fun backupTo(uri: android.net.Uri, remember: Boolean) {
        scope.launch {
            val now = System.currentTimeMillis()
            val ok = withContext(Dispatchers.IO) {
                if (remember) Backup.writeToFolder(context, repo, uri.toString())
                else runCatching { context.contentResolver.openOutputStream(uri)?.use { Backup.write(context, repo, it) } != null }.getOrDefault(false)
            }
            val s = repo.currentSettings()
            repo.saveSettings(if (ok) s.copy(lastBackupAt = now, backupTreeUri = if (remember) uri.toString() else s.backupTreeUri) else s)
            status = if (ok) "Backup saved." else "Could not save the backup there. Try another place."
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            backupTo(uri, true)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) backupTo(uri, false)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    fun save(s: ShopSettings) { scope.launch { repo.saveSettings(s) } }
    val currency = Locales.currency(settings.currencyCode)

    ScreenScaffold("Settings", onBack) {
        SectionLabel("Look")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Colour theme", fontWeight = FontWeight.SemiBold)
            Text("Tap to try. It changes the whole app straight away.", color = Muted, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (t in Themes.all) {
                    val on = Themes.current.id == t.id
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(role = Role.RadioButton) { save(settings.copy(themeId = t.id)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Brush.linearGradient(listOf(t.start, t.middle, t.end)), CircleShape)
                                .border(if (on) 3.dp else 1.dp, if (on) Ink else Muted.copy(alpha = 0.4f), CircleShape)
                        )
                        Text(
                            t.name,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        SectionLabel("Shop (template settings)")
        NavRow("Shop name, address and phone", settings.shopName, { editing = Editing.SHOP })
        NavRow("Country and currency", "${Locales.country(settings.countryCode).name} · ${currency.code}", { editing = Editing.REGION })
        NavRow("Tax (GST / VAT)", if (settings.taxEnabled) "On" else "Off", { editing = Editing.TAX })
        NavRow("Item code letters", "${settings.itemCodePrefix}-0001", { editing = Editing.CODE })

        SectionLabel("Stock and money")
        NavRow("Categories and questions", "", { onNavigate("categories") })
        NavRow("Services and charges", services.size.toString(), { onNavigate("services") })
        NavRow("Money accounts", "", { onNavigate("accounts") })
        NavRow("Expense and income types", "", { onNavigate("entry_types") })
        NavRow("Stock cost method (no-IMEI items)", "Average", null)

        SectionLabel("Selling")
        NavRow("Bill footer and receipt printer", "", { onNavigate("printing") })
        NavRow("Payment methods", "Soon", null)

        SectionLabel("Where your data lives")
        NavRow("On this phone", "Always on", null)
        NavRow(
            "Daily backup folder (pick one in Google Drive)",
            if (settings.backupTreeUri.isBlank()) "Not set" else "On",
            { folderPicker.launch(null) },
        )
        NavRow("Back up now to a file", if (settings.lastBackupAt > 0) "Last: " + BillPdf.dateText(settings.lastBackupAt) else "Never", { filePicker.launch(Backup.fileName()) })
        NavRow("Restore from a backup file", "", { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) })
        status?.let { Text(it, color = Muted, fontSize = 13.sp) }
        NavRow("Online backup (my Supabase)", if (settings.useSupabase) "On" else "Off", { onNavigate("cloud") })

        SectionLabel("People and safety")
        NavRow("Staff and roles", "${staff.size} people", { onNavigate("staff") })
        NavRow("PIN lock for the owner", if (staff.firstOrNull { it.role == "OWNER" }?.pinHash?.isNotEmpty() == true) "On" else "Off", { editing = Editing.PIN })
        NavRow("Export all data (opens in Excel)", "", {
            scope.launch {
                status = "Preparing..."
                val f = withContext(Dispatchers.IO) { app.phoneshop.manager.util.Export.create(context, repo, Session.canSeeCost) }
                app.phoneshop.manager.util.Export.share(context, f)
                status = "Choose where to save it. Open the files with Excel or Google Sheets."
            }
        })
        if (app.phoneshop.manager.util.Fingerprint.available(context)) {
            val owner = staff.firstOrNull { it.role == "OWNER" }
            var fp by remember { mutableStateOf(owner != null && app.phoneshop.manager.util.Prefs.fingerprintStaff(context) == owner.id) }
            SwitchRow(
                "Fingerprint unlock for the owner",
                if (owner?.pinHash?.isNotEmpty() == true) "Open the app with your fingerprint on this phone." else "Set a PIN first.",
                fp,
                { on ->
                    if (owner != null) {
                        if (on) app.phoneshop.manager.util.Fingerprint.ask(context, "Confirm your fingerprint", {
                            app.phoneshop.manager.util.Prefs.setFingerprintStaff(context, owner.id); fp = true
                        }) { status = it }
                        else { app.phoneshop.manager.util.Prefs.setFingerprintStaff(context, ""); fp = false }
                    }
                },
                enabled = owner?.pinHash?.isNotEmpty() == true,
            )
        }
    }

    when (editing) {
        Editing.NONE -> {}
        Editing.SHOP -> {
            var name by remember { mutableStateOf(settings.shopName) }
            var address by remember { mutableStateOf(settings.address) }
            var phone by remember { mutableStateOf(settings.phone) }
            EditDialog("Shop details", name.isNotBlank(), { editing = Editing.NONE }, {
                save(settings.copy(shopName = name.trim(), address = address.trim(), phone = phone.trim()))
                editing = Editing.NONE
            }) {
                GlassField(name, { name = it }, "Shop name *")
                GlassField(address, { address = it }, "Address")
                GlassField(phone, { phone = it }, "Shop phone / WhatsApp", keyboard = KeyboardType.Phone)
            }
        }
        Editing.REGION -> {
            var country by remember { mutableStateOf(settings.countryCode) }
            var cur by remember { mutableStateOf(settings.currencyCode) }
            EditDialog("Country and currency", true, { editing = Editing.NONE }, {
                save(settings.copy(countryCode = country, currencyCode = cur))
                editing = Editing.NONE
            }) {
                PickerField("Country", Locales.country(country).name, Locales.countries.map { it.name }, onPick = { picked ->
                    val c = Locales.countries.first { it.name == picked }
                    country = c.code
                    cur = c.currency
                })
                PickerField(
                    "Currency",
                    Locales.currency(cur).let { "${it.code} - ${it.name}" },
                    Locales.currencies.map { "${it.code} - ${it.name}" },
                    onPick = { picked -> cur = picked.substringBefore(" - ") },
                )
            }
        }
        Editing.TAX -> {
            var on by remember { mutableStateOf(settings.taxEnabled) }
            var number by remember { mutableStateOf(settings.taxNumber) }
            var rate by remember { mutableStateOf(if (settings.taxRateBp > 0) (settings.taxRateBp / 100.0).toString().removeSuffix(".0") else "") }
            EditDialog("Tax on bills", true, { editing = Editing.NONE }, {
                save(settings.copy(taxEnabled = on, taxNumber = number.trim(), taxRateBp = ((rate.toDoubleOrNull() ?: 0.0) * 100).toInt()))
                editing = Editing.NONE
            }) {
                SwitchRow("My shop charges tax on bills", "GST / VAT. Off by default.", on, { on = it })
                if (on) {
                    GlassField(number, { number = it }, "Tax number (shown on bills)")
                    GlassField(rate, { rate = it }, "Tax rate % (prices already include it)", keyboard = KeyboardType.Decimal)
                }
            }
        }
        Editing.PIN -> {
            val owner = staff.firstOrNull { it.role == "OWNER" }
            var pin by remember { mutableStateOf("") }
            EditDialog("PIN lock", pin.length >= 4, { editing = Editing.NONE }, {
                if (owner != null) scope.launch { repo.saveStaff(owner, owner.name, owner.role, pin) }
                editing = Editing.NONE
            }) {
                Text("The app asks for this PIN every time it opens. Write it down somewhere safe: if you forget it, a restore from backup is the only way back in.", color = Muted, fontSize = 13.sp)
                GlassField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "New PIN (4 to 8 digits)", keyboard = KeyboardType.NumberPassword)
                if (owner?.pinHash?.isNotEmpty() == true) {
                    TextButton(onClick = { scope.launch { repo.saveStaff(owner, owner.name, owner.role, "") }; editing = Editing.NONE }) { Text("Turn the PIN off", color = Bad) }
                }
            }
        }
        Editing.CODE -> {
            var letters by remember { mutableStateOf(settings.itemCodePrefix) }
            EditDialog("Item code letters", true, { editing = Editing.NONE }, {
                save(settings.copy(itemCodePrefix = letters.trim().uppercase().ifEmpty { "PH" }))
                editing = Editing.NONE
            }) {
                Text("New products get codes like ${letters.ifBlank { "PH" }.uppercase()}-0001. Products you already have keep their codes.", color = Muted, fontSize = 13.sp)
                GlassField(letters, { letters = it.filter { c -> c.isLetterOrDigit() }.take(6) }, "Letters")
            }
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Replace everything with this backup?",
            "All data on this phone is replaced by the backup, and the app restarts. This cannot be undone.",
            "Restore",
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    val error = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)?.use { Backup.restore(context, repo, it) } ?: "Could not open that file." }
                            .getOrElse { "That file could not be read." }
                    }
                    if (error == null) Backup.restart(context) else status = error
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

@Composable
fun EditDialog(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text("Save", color = Indigo, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
