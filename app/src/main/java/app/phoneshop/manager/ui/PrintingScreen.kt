package app.phoneshop.manager.ui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.util.Prefs
import app.phoneshop.manager.util.ReceiptPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Bill footer and receipt printer. Saved on this phone only. */
@SuppressLint("MissingPermission")
@Composable
fun PrintingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var footer by remember { mutableStateOf(Prefs.billFooter(context)) }
    var paper by remember { mutableStateOf(Prefs.paperMm(context)) }
    var printerName by remember { mutableStateOf(Prefs.printerName(context)) }
    var devices by remember { mutableStateOf(ReceiptPrinter.pairedPrinters(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        devices = ReceiptPrinter.pairedPrinters(context)
        if (devices.isEmpty()) status = "No paired printer found. Pair it first in the phone's Bluetooth settings."
    }

    ScreenScaffold("Bill footer and printer", onBack) {
        SectionLabel("Bill footer")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Printed at the bottom of every bill. Use new lines for more than one line.", color = Muted, fontSize = 13.sp)
            GlassField(footer, { footer = it }, "For example: Thank you! Goods can be exchanged within 7 days.")
        }
        SectionLabel("Paper width")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("58 mm", paper == 58) { paper = 58 }
            GlassChip("80 mm", paper == 80) { paper = 80 }
        }
        SectionLabel("Bluetooth receipt printer")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(if (printerName.isBlank()) "No printer chosen" else "Chosen: $printerName", fontWeight = FontWeight.SemiBold)
            Text("Pair the printer in the phone's Bluetooth settings first, then pick it below. Works with common ESC/POS thermal printers.", color = Muted, fontSize = 13.sp)
        }
        GlassButton("Find paired printers", {
            if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            else { devices = ReceiptPrinter.pairedPrinters(context); if (devices.isEmpty()) status = "No paired printer found." }
        }, Modifier.fillMaxWidth())
        devices.forEach { d ->
            val n = d.name ?: d.address
            GlassCard(Modifier.fillMaxWidth(), onClick = {
                Prefs.setPrinter(context, d.address, n)
                printerName = n
                status = "Printer chosen."
            }) { Text(n, fontWeight = FontWeight.SemiBold); Text(d.address, color = Muted, fontSize = 12.sp) }
        }
        GradientButton("Save", {
            Prefs.setBillFooter(context, footer.trim())
            Prefs.setPaperMm(context, paper)
            status = "Saved."
        }, Modifier.fillMaxWidth())
        GlassButton("Print a test receipt", {
            scope.launch {
                status = "Printing..."
                val cols = ReceiptPrinter.columns(paper)
                val text = "Test receipt\n" + "-".repeat(cols) + "\n" + footer + "\n"
                status = withContext(Dispatchers.IO) { ReceiptPrinter.print(context, text) } ?: "Sent to the printer."
            }
        }, Modifier.fillMaxWidth(), enabled = printerName.isNotBlank())
        status?.let { Text(it, color = Muted, fontSize = 13.sp) }
        Text("Not tested with every printer model. If yours prints nothing, tell me the model.", color = Muted, fontSize = 12.sp)
    }
}
