package app.phoneshop.manager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Locales
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(repo: Repository, initial: ShopSettings, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var shopName by rememberSaveable { mutableStateOf(initial.shopName) }
    var address by rememberSaveable { mutableStateOf(initial.address) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var country by rememberSaveable { mutableStateOf(initial.countryCode) }
    var currency by rememberSaveable { mutableStateOf(initial.currencyCode) }
    var taxOn by rememberSaveable { mutableStateOf(initial.taxEnabled) }
    var taxNo by rememberSaveable { mutableStateOf(initial.taxNumber) }
    var drive by rememberSaveable { mutableStateOf(initial.useDriveBackup) }
    var supabase by rememberSaveable { mutableStateOf(initial.useSupabase) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(20.dp)
    ) {
        Text("Set up your shop", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Step ${page + 1} of 3", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) {
                val color by animateColorAsState(if (i <= page) Indigo else Indigo.copy(alpha = 0.18f), label = "step")
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
        }

        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val forward = targetState > initialState
                    (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { if (forward) it / 4 else -it / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { if (forward) -it / 4 else it / 4 })
                }
            },
            label = "setup",
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (p) {
                    0 -> {
                        Text("Tell us about your shop. This appears on your bills.", color = Muted)
                        GlassField(shopName, { shopName = it; nameError = null }, "Shop name *", error = nameError)
                        GlassField(address, { address = it }, "Address")
                        GlassField(phone, { phone = it }, "Shop phone / WhatsApp", keyboard = KeyboardType.Phone)
                        Text("You can add your logo later in Settings.", color = Muted, fontSize = 13.sp)
                    }
                    1 -> {
                        Text("Choose your country and currency. You can change them any time in Settings.", color = Muted)
                        PickerField(
                            "Country",
                            Locales.country(country).name,
                            Locales.countries.map { it.name },
                            onPick = { picked ->
                            val c = Locales.countries.first { it.name == picked }
                            country = c.code
                            currency = c.currency
                            },
                        )
                        PickerField(
                            "Currency",
                            Locales.currency(currency).let { "${it.code} - ${it.name}" },
                            Locales.currencies.map { "${it.code} - ${it.name}" },
                            onPick = { picked -> currency = picked.substringBefore(" - ") },
                        )
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("My shop charges tax on bills", fontWeight = FontWeight.SemiBold)
                                    Text("GST / VAT. Off by default.", color = Muted, fontSize = 13.sp)
                                }
                                Switch(checked = taxOn, onCheckedChange = { taxOn = it })
                            }
                            if (taxOn) {
                                Box(Modifier.padding(top = 10.dp)) {
                                    GlassField(taxNo, { taxNo = it }, "Tax number (shown on bills)")
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Where should your shop data live?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("You can use any mix and change it later without losing data.", color = Muted)
                        OptionCard(
                            "On this phone",
                            "Always on. Everything is saved here first, so the app works without internet.",
                            checked = true,
                            locked = true,
                            onChange = {},
                        )
                        OptionCard(
                            "Back up to my Google Drive",
                            "A private daily backup in your own Drive. Arrives in the next update; your choice is saved.",
                            checked = drive,
                            onChange = { drive = it },
                        )
                        OptionCard(
                            "Sync online with my Supabase",
                            "Share data between phones and staff. Arrives in a later update; your choice is saved.",
                            checked = supabase,
                            onChange = { supabase = it },
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) {
                GlassButton("Back", { page -= 1 }, Modifier.weight(1f))
            }
            GradientButton(
                if (page < 2) "Next" else "Finish",
                onClick = {
                    if (page == 0 && shopName.isBlank()) {
                        nameError = "Please enter your shop name"
                    } else if (page < 2) {
                        page += 1
                    } else {
                        scope.launch {
                            repo.saveSettings(
                                ShopSettings(
                                    shopName = shopName.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    countryCode = country,
                                    currencyCode = currency,
                                    taxEnabled = taxOn,
                                    taxNumber = taxNo.trim(),
                                    useDriveBackup = drive,
                                    useSupabase = supabase,
                                    setupDone = true,
                                )
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.weight(if (page > 0) 1f else 2f),
            )
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    locked: Boolean = false,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text, color = Muted, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = !locked)
        }
    }
}
