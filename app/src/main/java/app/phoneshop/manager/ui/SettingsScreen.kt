package app.phoneshop.manager.ui

import androidx.compose.foundation.background
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

private enum class Editing { NONE, SHOP, REGION, TAX, CODE }

@Composable
fun SettingsScreen(
    repo: Repository,
    settings: ShopSettings,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val services by repo.services.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(Editing.NONE) }
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
        NavRow("Bill layout and footer", "Soon", null)
        NavRow("Receipt printer", "Soon", null)
        NavRow("Payment methods", "Soon", null)

        SectionLabel("Where your data lives")
        NavRow("On this phone", "Always on", null)
        SwitchRow(
            "Back up to my Google Drive",
            "Arrives in the last update. Your choice is saved.",
            settings.useDriveBackup,
            { save(settings.copy(useDriveBackup = it)) },
        )
        SwitchRow(
            "Sync online with my Supabase",
            "Arrives in the last update. Your choice is saved.",
            settings.useSupabase,
            { save(settings.copy(useSupabase = it)) },
        )

        SectionLabel("People and safety")
        NavRow("Staff and roles", "Soon", null)
        NavRow("PIN / fingerprint lock", "Soon", null)
        NavRow("Export all data (Excel)", "Soon", null)
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
            EditDialog("Tax on bills", true, { editing = Editing.NONE }, {
                save(settings.copy(taxEnabled = on, taxNumber = number.trim()))
                editing = Editing.NONE
            }) {
                SwitchRow("My shop charges tax on bills", "GST / VAT. Off by default.", on, { on = it })
                if (on) GlassField(number, { number = it }, "Tax number (shown on bills)")
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
