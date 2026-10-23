package app.phoneshop.manager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.PaymentInput
import app.phoneshop.manager.data.RepairJob
import app.phoneshop.manager.data.Repository
import app.phoneshop.manager.data.ShopSettings
import app.phoneshop.manager.util.BillPdf
import app.phoneshop.manager.util.Locales
import app.phoneshop.manager.util.Money
import kotlinx.coroutines.launch

fun jobLabel(no: Int) = "R-" + no.toString().padStart(3, '0')

private val steps = listOf(
    "RECEIVED" to "Received",
    "CHECKING" to "Checking",
    "WAITING_PARTS" to "Waiting for parts",
    "REPAIRING" to "Repairing",
    "READY" to "Ready for pickup",
    "DELIVERED" to "Delivered",
)

private fun stepName(status: String) = steps.firstOrNull { it.first == status }?.second ?: "Cancelled"

private fun isLate(j: RepairJob) =
    j.readyBy != null && j.readyBy < System.currentTimeMillis() && j.status !in listOf("READY", "DELIVERED", "CANCELLED")

private fun dayOnly(millis: Long?): String =
    if (millis == null) "" else java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

private val workOptions = listOf("Screen replacement", "Battery", "Charging port", "Software")

/** More → Repairs. */
@Composable
fun RepairsScreen(
    repo: Repository,
    settings: ShopSettings,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val jobs by repo.repairJobs.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    var filter by rememberSaveable { mutableStateOf("OPEN") }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val open = jobs.filter { it.status != "DELIVERED" && it.status != "CANCELLED" }
    val shown = jobs.filter { j ->
        when (filter) {
            "OPEN" -> j in open
            "DELIVERED" -> j.status == "DELIVERED"
            else -> j.status == filter
        } && (q.isEmpty() || jobLabel(j.jobNo).contains(q, true) || j.customerName.contains(q, true) ||
            j.phone.contains(q, true) || j.imei.contains(q, true) || j.device.contains(q, true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar("Repairs", onBack = onBack)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassField(query, { query = it }, "Search job no., name, phone or IMEI")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GlassChip("Open (${open.size})", filter == "OPEN") { filter = "OPEN" } }
                    item { GlassChip("Received ${jobs.count { it.status == "RECEIVED" }}", filter == "RECEIVED") { filter = "RECEIVED" } }
                    item { GlassChip("Waiting parts ${jobs.count { it.status == "WAITING_PARTS" }}", filter == "WAITING_PARTS") { filter = "WAITING_PARTS" } }
                    item { GlassChip("Ready ${jobs.count { it.status == "READY" }}", filter == "READY") { filter = "READY" } }
                    item { GlassChip("Delivered", filter == "DELIVERED") { filter = "DELIVERED" } }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) EmptyCard("No jobs here", "Tap \"+ New job\" when a phone comes in for repair.")
                shown.forEach { j ->
                    val toCollect = (if (j.status == "DELIVERED") 0L else (j.estimate - j.advance)).coerceAtLeast(0)
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(j.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${jobLabel(j.jobNo)} · ${j.device}", fontWeight = FontWeight.SemiBold)
                                Text("${j.customerName.ifBlank { "Walk-in" }} · ${j.work.ifBlank { j.problem }}", color = Muted, fontSize = 12.sp, maxLines = 2)
                                if (isLate(j)) StatusPill("Late", Color(0xFFFEE2E2), Color(0xFF991B1B))
                                else StatusPill(stepName(j.status), Color(0xFFDBEAFE), Color(0xFF1E40AF))
                                if (j.readyBy != null && j.status != "DELIVERED") Text("Promised ${dayOnly(j.readyBy)}", color = Muted, fontSize = 12.sp)
                            }
                            if (toCollect > 0) Text(Money.format(toCollect, currency), fontWeight = FontWeight.ExtraBold, color = Warn)
                        }
                    }
                }
            }
        }
        GradientButton("+ New job", onNew, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

/** A new job card, or editing one. The advance goes into the chosen account straight away. */
@Composable
fun JobFormScreen(repo: Repository, settings: ShopSettings, jobId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<RepairJob?>(null) }
    var customerId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var lockNote by remember { mutableStateOf("") }
    var cameWith by remember { mutableStateOf("") }
    val work = remember { mutableStateListOf<String>() }
    var otherWork by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf("") }
    var advance by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var readyBy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(jobId) {
        if (jobId != null) {
            val j = repo.getRepairJob(jobId) ?: return@LaunchedEffect
            existing = j; customerId = j.customerId; name = j.customerName; phone = j.phone; device = j.device; imei = j.imei
            lockNote = j.lockNote; cameWith = j.cameWith; problem = j.problem
            estimate = if (j.estimate > 0) Money.toInput(j.estimate) else ""
            readyBy = dayOnly(j.readyBy)
            j.work.split(", ").filter { it.isNotBlank() }.forEach { w -> if (w in workOptions) work.add(w) else otherWork = w }
        }
    }
    val chosenAccount = accountId ?: views.firstOrNull()?.account?.id
    val suggestions = if (existing == null && name.length >= 2) contacts.filter { it.kind == CUSTOMER && it.name.contains(name, true) && it.id != customerId }.take(3) else emptyList()

    fun save() {
        if (device.isBlank()) { error = "Enter the phone brand and model."; return }
        val est = if (estimate.isBlank()) 0L else (Money.parse(estimate) ?: run { error = "The estimate is not a number."; return })
        val adv = if (advance.isBlank()) 0L else (Money.parse(advance) ?: run { error = "The advance is not a number."; return })
        val by = if (readyBy.isBlank()) null else (parseDay(readyBy) ?: run { error = "Write the ready-by date like 2026-10-25."; return })
        val workText = (work.toList() + listOfNotNull(otherWork.trim().ifEmpty { null })).joinToString(", ")
        scope.launch {
            val e = existing
            if (e == null) {
                val id = repo.createRepairJob(customerId, name, phone, device, imei, lockNote, cameWith, workText, problem, est, adv, chosenAccount, by)
                if (id != null) onSaved(id)
            } else {
                repo.updateRepairJob(e.copy(customerName = name.trim(), phone = phone.trim(), device = device.trim(), imei = imei.trim(),
                    lockNote = lockNote.trim(), cameWith = cameWith.trim(), work = workText, problem = problem.trim(), estimate = est, readyBy = by))
                onSaved(e.id)
            }
        }
    }

    ScreenScaffold(
        if (jobId == null) "New repair job" else "Edit job",
        onBack,
        bottom = { GradientButton("Save job card", { save() }, Modifier.fillMaxWidth()) },
    ) {
        SectionLabel("Customer")
        GlassField(name, { name = it; customerId = null }, "Name")
        suggestions.forEach { c ->
            GlassButton("Use ${c.name} ${c.phone}", { customerId = c.id; name = c.name; phone = c.phone }, Modifier.fillMaxWidth())
        }
        GlassField(phone, { phone = it }, "Phone / WhatsApp", keyboard = KeyboardType.Phone)
        SectionLabel("Device")
        GlassField(device, { device = it }, "Brand and model *")
        GlassField(imei, { imei = it }, "IMEI", keyboard = KeyboardType.Number)
        GlassField(lockNote, { lockNote = it }, "Screen lock / pattern")
        GlassField(cameWith, { cameWith = it }, "Came with (SIM, back cover ...)")
        SectionLabel("Work needed")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workOptions) { w -> GlassChip(w, w in work) { if (w in work) work.remove(w) else work.add(w) } }
        }
        GlassField(otherWork, { otherWork = it }, "Other work")
        GlassField(problem, { problem = it }, "Problem in the customer's words")
        SectionLabel("Money and time")
        GlassField(estimate, { estimate = it }, "Estimate (${currency.code})", keyboard = KeyboardType.Decimal)
        if (existing == null) {
            GlassField(advance, { advance = it }, "Advance paid (${currency.code})", keyboard = KeyboardType.Decimal)
            if (advance.isNotBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(views) { v -> GlassChip(v.account.name, v.account.id == chosenAccount) { accountId = v.account.id } }
                }
            }
        }
        GlassField(readyBy, { readyBy = it }, "Ready by (YYYY-MM-DD)")
        if (existing == null) Text("The advance goes into the account now. Share the job number with the customer as their receipt.", color = Muted, fontSize = 13.sp)
        error?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
    }
}

/** One job: status steps, parts used, money and handing the phone back. */
@Composable
fun JobDetailScreen(repo: Repository, settings: ShopSettings, jobId: String, onBack: () -> Unit, onEdit: () -> Unit, onOpenBill: (String) -> Unit) {
    val job by repo.observeRepairJob(jobId).collectAsState(initial = null)
    val allParts by repo.repairParts.collectAsState(initial = emptyList())
    val categories by repo.categories.collectAsState(initial = emptyList())
    val products by repo.products.collectAsState(initial = emptyList())
    val views by repo.accountViews.collectAsState(initial = emptyList())
    val currency = Locales.currency(settings.currencyCode)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pickPart by remember { mutableStateOf(false) }
    var partQty by remember { mutableStateOf<app.phoneshop.manager.data.Product?>(null) }
    var outsideDialog by remember { mutableStateOf(false) }
    var deliver by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val j = job
    val parts = allParts.filter { it.jobId == jobId }

    ScreenScaffold(
        if (j != null) "#${jobLabel(j.jobNo)} · ${j.device}" else "Job",
        onBack,
        actions = { TextButton(onClick = onEdit, enabled = j != null && j.status != "DELIVERED") { Text("Edit", color = Indigo, fontWeight = FontWeight.SemiBold) } },
    ) {
        if (j == null) return@ScreenScaffold
        val done = j.status == "DELIVERED"
        val partsCost = parts.sumOf { it.costTotal }
        val charge = if (done) j.charge else j.estimate
        val toCollect = (charge - j.advance).coerceAtLeast(0)

        GlassCard(Modifier.fillMaxWidth().riseIn(0)) {
            Text(j.customerName.ifBlank { "Walk-in" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(listOf(j.work, "in ${BillPdf.dateText(j.createdAt)}").filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp)
            if (j.problem.isNotBlank()) Text("“${j.problem}”", color = Muted, fontSize = 13.sp)
            if (j.imei.isNotBlank()) Text("IMEI ${j.imei}", color = Muted, fontSize = 12.sp)
            if (j.lockNote.isNotBlank() || j.cameWith.isNotBlank()) Text(listOf(j.lockNote, j.cameWith).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
            if (j.phone.isNotBlank()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Call", { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${j.phone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.weight(1f))
                    GlassButton("WhatsApp: ready", {
                        val digits = j.phone.filter { it.isDigit() }
                        val text = "Hello ${j.customerName}, your ${j.device} (job ${jobLabel(j.jobNo)}) is ready for pickup at ${settings.shopName}."
                        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text))
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.weight(1f))
                }
            }
        }

        if (!done && j.status != "CANCELLED") {
            SectionLabel("Status (tap to move forward)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(steps) { (code, label) ->
                    GlassChip(label, j.status == code) {
                        if (code == "DELIVERED") deliver = true else scope.launch { repo.setRepairStatus(jobId, code, if (code == "REPAIRING") "Owner" else null) }
                    }
                }
            }
        } else {
            StatusPill(stepName(j.status), Color(0xFFDCFCE7), Color(0xFF166534))
        }

        SectionLabel("Parts used (taken from stock)")
        if (parts.isEmpty()) Text("No parts added.", color = Muted)
        parts.forEach { p ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.name} ×${p.qty}", fontWeight = FontWeight.SemiBold)
                        Text("cost ${Money.format(p.costTotal, currency)}", color = Muted, fontSize = 12.sp)
                    }
                    if (!done) TextButton(onClick = { scope.launch { repo.removeRepairPart(p.id) } }) { Text("Remove", color = Bad) }
                }
            }
        }
        message?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }
        if (!done) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("+ Add part", { pickPart = true }, Modifier.weight(1f))
                GlassButton("Outside work cost", { outsideDialog = true }, Modifier.weight(1f))
            }
        }
        if (j.outsideCost > 0) Text("Outside work cost: ${Money.format(j.outsideCost, currency)}", color = Muted)

        SectionLabel("Money")
        GlassCard(Modifier.fillMaxWidth()) {
            JobRow(if (done) "Repair charge" else "Estimate", Money.format(charge, currency))
            JobRow("Advance paid", "− " + Money.format(j.advance, currency))
            JobRow(if (done) "Paid at delivery or owed" else "To collect", Money.format(toCollect, currency), bold = true)
            JobRow("Job profit (owner only)", Money.format(charge - partsCost - j.outsideCost, currency))
            if (done) JobRow("Repair warranty", "${j.warrantyDays} days")
        }
        if (done && j.saleId != null) GlassButton("Open the bill", { onOpenBill(j.saleId) }, Modifier.fillMaxWidth())
        if (!done && j.status != "CANCELLED") {
            GradientButton("Deliver and take payment", { deliver = true }, Modifier.fillMaxWidth())
        }
    }

    if (pickPart) ProductPickerDialog(repo, { pickPart = false }) { p ->
        val tracks = categories.firstOrNull { it.id == p.categoryId }?.tracksImei == true
        if (tracks) message = "Phones tracked by IMEI cannot be used as repair parts here." else partQty = p
    }
    partQty?.let { p ->
        var qty by remember(p) { mutableStateOf("1") }
        EditDialog("${p.name}: how many?", (qty.toIntOrNull() ?: 0) > 0, { partQty = null }, {
            scope.launch { message = repo.addRepairPart(jobId, p.id, p.name, qty.toIntOrNull() ?: 0) }
            partQty = null
        }) { GlassField(qty, { qty = it }, "Quantity", keyboard = KeyboardType.Number) }
    }
    if (outsideDialog && job != null) {
        val cur = job!!
        var cost by remember { mutableStateOf(if (cur.outsideCost > 0) Money.toInput(cur.outsideCost) else "") }
        EditDialog("Outside work cost", cost.isBlank() || Money.parse(cost) != null, { outsideDialog = false }, {
            scope.launch { repo.updateRepairJob(cur.copy(outsideCost = if (cost.isBlank()) 0L else (Money.parse(cost) ?: 0L))) }
            outsideDialog = false
        }) {
            Text("What you paid another workshop for this job. It counts against the job's profit.", color = Muted, fontSize = 13.sp)
            GlassField(cost, { cost = it }, "Cost (${currency.code})", keyboard = KeyboardType.Decimal)
        }
    }
    if (deliver && job != null) {
        val cur = job!!
        var charge by remember { mutableStateOf(Money.toInput(if (cur.estimate > 0) cur.estimate else 0L)) }
        var warranty by remember { mutableStateOf(cur.warrantyDays.toString()) }
        var accountId by remember { mutableStateOf<String?>(null) }
        var payNow by remember { mutableStateOf<String?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        val chargeMinor = Money.parse(charge) ?: 0L
        val toPay = (chargeMinor - cur.advance).coerceAtLeast(0)
        val acc = accountId ?: views.firstOrNull()?.account?.id
        val pay = payNow ?: Money.toInput(toPay)
        EditDialog("Deliver and take payment", Money.parse(charge) != null, { deliver = false }, {
            val amount = Money.parse(pay) ?: 0L
            scope.launch {
                err = repo.deliverRepair(jobId, chargeMinor, if (amount > 0 && acc != null) listOf(PaymentInput(acc, amount)) else emptyList(), warranty.toIntOrNull() ?: 30)
                if (err == null) deliver = false
            }
        }) {
            GlassField(charge, { charge = it; payNow = null }, "Repair charge (${currency.code})", keyboard = KeyboardType.Decimal)
            Text("Advance already paid: ${Money.format(cur.advance, currency)}. To collect: ${Money.format(toPay, currency)}", color = Muted, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(views) { v -> GlassChip(v.account.name, v.account.id == acc) { accountId = v.account.id } }
            }
            GlassField(pay, { payNow = it }, "Paid now (${currency.code})", keyboard = KeyboardType.Decimal)
            GlassField(warranty, { warranty = it }, "Repair warranty (days)", keyboard = KeyboardType.Number)
            err?.let { Text(it, color = Bad, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun JobRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (bold) Warn else Ink)
    }
}
