package app.phoneshop.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
                .setPrimaryClip(ClipData.newPlainText("Setup script", CloudBackup.SETUP_SQL))
            status = "Copied. Paste it in the Supabase SQL Editor."
        }, Modifier.fillMaxWidth())
        SectionLabel("Step 2 - connect")
        GlassField(url, { url = it }, "Project URL (https://...supabase.co)", keyboard = KeyboardType.Uri)
        GlassField(key, { key = it }, "anon public key")
        GlassField(email, { email = it }, "Login email", keyboard = KeyboardType.Email)
        GlassField(password, { password = it }, "Login password", keyboard = KeyboardType.Password)
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
