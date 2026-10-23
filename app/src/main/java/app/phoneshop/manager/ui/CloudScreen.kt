package app.phoneshop.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.Backup
import app.phoneshop.manager.util.CloudBackup
import app.phoneshop.manager.util.Prefs
import app.phoneshop.manager.util.SyncEngine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CloudScreen(repo: Repository, settings: ShopSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { CloudBackup.load(context) }
    var url by remember { mutableStateOf(saved.url) }
    var key by remember { mutableStateOf(saved.key) }
    var email by remember { mutableStateOf(saved.email) }
    var password by remember { mutableStateOf(saved.password) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<java.io.File?>(null) }

    fun config() = CloudBackup.Config(url.trim().trimEnd('/'), key.trim(), email.trim(), password)
    fun run(work: suspend () -> String?) {
        scope.launch {
            busy = true
            status = "Working..."
            status = work()
            busy = false
        }
    }

    ScreenScaffold("Online backup", onBack) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Your own online copy", fontWeight = FontWeight.SemiBold)
            Text(
                "This keeps one safe copy of your whole shop in your own free Supabase project. " +
                    "It is a backup you press, not live sharing between phones. The address and key stay on this phone only.",
                color = Muted, fontSize = 13.sp,
            )
        }
        SectionLabel("Step 1 - once, on supabase.com")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "1. Make a free account and a new project.\n" +
                    "2. Open Authentication > Users > Add user. Type an email and password you will remember, and tick Auto confirm.\n" +
                    "3. Open SQL Editor, paste the setup script (copy it below), and press Run.\n" +
                    "4. Open Project Settings > API. Copy the Project URL and the anon public key into the boxes below.",
                color = Muted, fontSize = 13.sp,
            )
        }
        GlassButton("Copy the setup script", {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Setup script", CloudBackup.setupSql(context)))
            status = "Copied. Paste it in the Supabase SQL Editor."
        }, Modifier.fillMaxWidth())
        SectionLabel("Step 2 - connect")
        GlassField(url, { url = it }, "Project URL (https://...supabase.co)", keyboard = KeyboardType.Uri)
        GlassField(key, { key = it }, "anon public key")
        GlassField(email, { email = it }, "Login email", keyboard = KeyboardType.Email)
        GlassField(password, { password = it }, "Login password", keyboard = KeyboardType.Password, hidden = true)
        GradientButton("Save and test", {
            val c = config()
            CloudBackup.save(context, c)
            run {
                val err = withContext(Dispatchers.IO) { CloudBackup.test(c) }
                repo.saveSettings(repo.currentSettings().copy(useSupabase = err == null))
                err ?: "Connected. You can back up now."
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && config().ready)
        SectionLabel("Step 3 - use it")
        GradientButton("Back up now", {
            val c = config()
            run {
                val err = withContext(Dispatchers.IO) { CloudBackup.upload(context, repo, c) }
                if (err == null) {
                    repo.saveSettings(repo.currentSettings().copy(lastBackupAt = System.currentTimeMillis()))
                    "Online backup saved."
                } else err
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && settings.useSupabase)
        GlassButton("Restore from online backup", {
            val c = config()
            run {
                val (file, err) = withContext(Dispatchers.IO) { CloudBackup.download(context, c) }
                if (file != null) { confirmRestore = file; "Found a backup." } else err
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && settings.useSupabase)
        SectionLabel("Share with other phones (live)")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Several phones, one shop", fontWeight = FontWeight.SemiBold)
            Text(
                "Every phone keeps working without internet. When there is internet, each phone sends its changes and fetches the others' about every 20 seconds. " +
                    "If two phones change the same record, the newer change wins. Stock is recalculated from the records, so it stays correct. " +
                    "Set the phones' clocks to automatic time.",
                color = Muted, fontSize = 13.sp,
            )
        }
        var sharing by remember { mutableStateOf(Prefs.shopId(context).isNotBlank()) }
        var slot by remember { mutableStateOf(2) }
        var confirmJoin by remember { mutableStateOf(false) }
        if (!sharing) {
            GradientButton("This is the FIRST phone: share my data", {
                val c = config()
                run {
                    val err = withContext(Dispatchers.IO) { SyncEngine.startAsMain(context, c, settings.shopName) }
                    if (err == null) { sharing = true; "Live sharing is on. Your data goes online in the next minute." } else err
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && settings.useSupabase)
            Text("On the SECOND phone only: pick its number (each phone needs its own, so bill numbers never clash):", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 2..5) GlassChip("Phone $n", slot == n) { slot = n }
            }
            GlassButton("SECOND phone only: join the shop", { confirmJoin = true }, Modifier.fillMaxWidth(), enabled = !busy && settings.useSupabase)
            if (confirmJoin) ConfirmDialog(
                "Delete this phone's data and join the shop?",
                "Only press this on a SECOND phone. All shop data on THIS phone is deleted and replaced by the shop's data from Supabase. On your first phone, press the other button instead.",
                "Delete and join",
                onConfirm = {
                    confirmJoin = false
                    val c = config()
                    run {
                        val err = withContext(Dispatchers.IO) { SyncEngine.joinShop(context, repo, c, slot) }
                        if (err == null) { sharing = true; "Joined. The shop's data arrives in the next minute." } else err
                    }
                },
                onDismiss = { confirmJoin = false },
            )
        } else {
            Text(Prefs.syncStatus(context).ifBlank { "Starting..." }, color = Good, fontSize = 13.sp)
            GlassButton("Turn live sharing off on this phone", { SyncEngine.stop(context); sharing = false; status = "Sharing is off." }, Modifier.fillMaxWidth())
        }
        if (!settings.useSupabase) Text("Connect first (steps above) for this to work.", color = Warn, fontSize = 13.sp)
        status?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
    }

    confirmRestore?.let { file ->
        ConfirmDialog(
            "Replace everything with the online backup?",
            "All data on this phone is replaced and the app restarts. This cannot be undone.",
            "Restore",
            onConfirm = {
                confirmRestore = null
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        runCatching { file.inputStream().use { Backup.restore(context, repo, it) } }.getOrElse { "That backup could not be read." }
                    }
                    if (err == null) Backup.restart(context) else status = err
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}
