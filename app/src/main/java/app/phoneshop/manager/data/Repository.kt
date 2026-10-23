package app.phoneshop.manager.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

data class StockInfo(val qty: Int, val minCost: Long, val maxCost: Long, val totalCost: Long)

/** An account with its current balance and what moved today. */
data class AccountView(val account: Account, val balance: Long, val todayIn: Long, val todayOut: Long)

/** One line in the cart before the sale is saved. */
data class CartLine(
    val key: String,
    val kind: String,
    val productId: String?,
    val name: String,
    val qty: Int,
    val unitPrice: Long,
    val unitId: String?,
    val imei: String,
    val warrantyMonths: Int,
    val tracksImei: Boolean,
    val minPrice: Long = 0L,
)

data class PaymentInput(val accountId: String, val amount: Long)

/** The outcome of saving a bill: the new bill id, or a message to show. */
data class SaleResult(val saleId: String?, val error: String?)

/** Goods that actually arrived. imeis has one entry per phone when the product is tracked by IMEI. */
data class ReceiveLine(
    val productId: String,
    val name: String,
    val qty: Int,
    val unitCost: Long,
    val tracksImei: Boolean,
    val imeis: List<String>,
    val poLineId: String? = null,
)

data class PoLineInput(val productId: String, val name: String, val qty: Int, val unitCost: Long)

data class UnitInput(val imei1: String, val imei2: String)

fun formatCode(prefix: String, n: Int): String =
    (if (prefix.isBlank()) "" else "${prefix.trim().uppercase()}-") + n.toString().padStart(4, '0')

fun isValidImei(value: String): Boolean = value.length == 15 && value.all { it.isDigit() }

class Repository(private val db: AppDatabase) {
    private val settingsDao = db.settingsDao()
    private val categoryDao = db.categoryDao()
    private val productDao = db.productDao()
    private val serviceDao = db.serviceDao()
    private val contactDao = db.contactDao()
    private val moneyDao = db.moneyDao()
    private val saleDao = db.saleDao()
    private val purchaseDao = db.purchaseDao()
    private val repairDao = db.repairDao()

    /** Never null once loaded; a shop that has not finished setup gets defaults. */
    val settings: Flow<ShopSettings> = settingsDao.observe().map { it ?: ShopSettings() }
    val categories: Flow<List<Category>> = categoryDao.observeCategories()
    val fields: Flow<List<CategoryField>> = categoryDao.observeFields()
    val products: Flow<List<Product>> = productDao.observeProducts()
    val services: Flow<List<Service>> = serviceDao.observe()
    val contacts: Flow<List<Contact>> = contactDao.observeAll()

    val accounts: Flow<List<Account>> = moneyDao.observeAccounts()
    val entryTypes: Flow<List<EntryType>> = moneyDao.observeTypes()
    val entries: Flow<List<MoneyEntry>> = moneyDao.observeEntries()

    val accountViews: Flow<List<AccountView>> = combine(accounts, entries) { accs, all ->
        val dayStart = startOfToday()
        accs.map { a ->
            val mine = all.filter { it.accountId == a.id && it.voidedAt == null }
            val today = mine.filter { it.entryDate >= dayStart }
            AccountView(
                a,
                a.openingBalance + mine.sumOf { it.amount },
                today.filter { it.amount > 0 }.sumOf { it.amount },
                -today.filter { it.amount < 0 }.sumOf { it.amount },
            )
        }
    }

    private fun startOfToday(): Long = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun newEntry(kind: String, accountId: String, amount: Long, typeId: String?, note: String, group: String) =
        MoneyEntry(
            id = UUID.randomUUID().toString(), kind = kind, accountId = accountId, amount = amount,
            typeId = typeId, contactId = null, refType = "", refId = "", groupId = group,
            note = note.trim(), entryDate = System.currentTimeMillis(), createdAt = System.currentTimeMillis(),
        )

    /** Money out for a shop cost. Returns an error message or null. */
    suspend fun recordExpense(accountId: String, typeId: String?, amount: Long, note: String): String? {
        if (amount <= 0) return "Enter an amount above zero."
        moneyDao.insertEntry(newEntry("EXPENSE", accountId, -amount, typeId, note, UUID.randomUUID().toString()))
        return null
    }

    suspend fun recordIncome(accountId: String, typeId: String?, amount: Long, note: String): String? {
        if (amount <= 0) return "Enter an amount above zero."
        moneyDao.insertEntry(newEntry("INCOME", accountId, amount, typeId, note, UUID.randomUUID().toString()))
        return null
    }

    /** Money the owner puts into or takes out of the shop. Not income and not an expense. */
    suspend fun recordOwner(accountId: String, amount: Long, putIn: Boolean, note: String): String? {
        if (amount <= 0) return "Enter an amount above zero."
        val kind = if (putIn) "OWNER_IN" else "OWNER_OUT"
        moneyDao.insertEntry(newEntry(kind, accountId, if (putIn) amount else -amount, null, note, UUID.randomUUID().toString()))
        return null
    }

    /** Moves the shop's own money between two accounts. Profit does not change. */
    suspend fun recordTransfer(fromId: String, toId: String, amount: Long, note: String): String? = db.withTransaction {
        if (amount <= 0) return@withTransaction "Enter an amount above zero."
        if (fromId == toId) return@withTransaction "Choose two different accounts."
        val group = UUID.randomUUID().toString()
        moneyDao.insertEntry(newEntry("TRANSFER", fromId, -amount, null, note, group))
        moneyDao.insertEntry(newEntry("TRANSFER", toId, amount, null, note, group))
        null
    }

    /** Cancels an entry (and its other half for transfers). It stays in the records with the reason. */
    suspend fun voidEntry(groupId: String, reason: String) =
        moneyDao.voidGroup(groupId, System.currentTimeMillis(), reason.trim())

    suspend fun saveAccount(existing: Account?, name: String, type: String, detail: String, opening: Long) {
        moneyDao.upsertAccount(
            Account(
                existing?.id ?: "acc_" + UUID.randomUUID().toString().take(8),
                name.trim(), type, detail.trim(), opening,
                existing?.sortOrder ?: (System.currentTimeMillis() / 1000).toInt(),
            )
        )
    }

    /** Only an account with no entries can be removed. Returns an error or null. */
    suspend fun deleteAccount(id: String): String? {
        if (moneyDao.entryCountForAccount(id) > 0) return "This account has money entries, so it cannot be removed."
        moneyDao.softDeleteAccount(id, System.currentTimeMillis())
        return null
    }

    suspend fun saveEntryType(existing: EntryType?, name: String, kind: String) {
        moneyDao.upsertType(
            EntryType(existing?.id ?: "type_" + UUID.randomUUID().toString().take(8), name.trim(), kind,
                existing?.sortOrder ?: (System.currentTimeMillis() / 1000).toInt())
        )
    }

    suspend fun deleteEntryType(id: String) = moneyDao.softDeleteType(id, System.currentTimeMillis())

    val repairJobs: Flow<List<RepairJob>> = repairDao.observeJobs()
    val repairParts: Flow<List<RepairPart>> = repairDao.observeParts()
    suspend fun getRepairJob(id: String): RepairJob? = repairDao.getJob(id)
    fun observeRepairJob(id: String): Flow<RepairJob?> = repairDao.observeJob(id)

    /** Takes qty items off the shelf (oldest batch first) and returns their average cost, or null if there is not enough. */
    private suspend fun takeFromBatches(productId: String, qty: Int): Long? {
        val batches = productDao.batchesWithStock(productId)
        val available = batches.sumOf { it.remaining }
        if (available < qty || qty <= 0) return null
        val value = batches.sumOf { it.remaining.toLong() * it.unitCost }
        var need = qty
        for (b in batches) {
            if (need == 0) break
            val take = minOf(need, b.remaining)
            productDao.upsertBatch(b.copy(remaining = b.remaining - take))
            need -= take
        }
        return value * qty / available
    }

    /** Opens a job card. An advance goes into the chosen account straight away. */
    suspend fun createRepairJob(
        customerId: String?,
        customerName: String,
        phone: String,
        device: String,
        imei: String,
        lockNote: String,
        cameWith: String,
        work: String,
        problem: String,
        estimate: Long,
        advance: Long,
        advanceAccountId: String?,
        readyBy: Long?,
    ): String? = db.withTransaction {
        val settings = settingsDao.get() ?: ShopSettings()
        val now = System.currentTimeMillis()
        // A named customer gets a contact automatically so the job and its bill show on their profile.
        var cid = customerId
        if (cid == null && customerName.isNotBlank()) {
            cid = UUID.randomUUID().toString()
            contactDao.upsert(Contact(cid, "CUSTOMER", customerName.trim(), phone.trim(), "", "", 0L, now, now))
        }
        val id = UUID.randomUUID().toString()
        repairDao.upsertJob(
            RepairJob(
                id, settings.nextJobNumber, cid, customerName.trim(), phone.trim(), device.trim(), imei.trim(), lockNote.trim(),
                cameWith.trim(), work, problem.trim(), estimate, advance, readyBy, "RECEIVED", 0L, 0L, 30, "", null, now,
            )
        )
        settingsDao.save(settings.copy(nextJobNumber = settings.nextJobNumber + 1))
        if (advance > 0 && advanceAccountId != null) {
            moneyDao.insertEntry(
                newEntry("REPAIR_ADVANCE", advanceAccountId, advance, null, "Repair #R-${settings.nextJobNumber.toString().padStart(3, '0')} advance", id)
                    .copy(contactId = cid, refType = "REPAIR", refId = id)
            )
        }
        id
    }

    suspend fun updateRepairJob(job: RepairJob) = repairDao.upsertJob(job)

    suspend fun setRepairStatus(id: String, status: String, assignedTo: String? = null) {
        repairDao.getJob(id)?.let { repairDao.upsertJob(it.copy(status = status, assignedTo = assignedTo ?: it.assignedTo)) }
    }

    /** Takes a part from stock for this job at its average cost. Returns an error or null. */
    suspend fun addRepairPart(jobId: String, productId: String, name: String, qty: Int): String? = db.withTransaction {
        val cost = takeFromBatches(productId, qty) ?: return@withTransaction "Not enough of $name in stock."
        repairDao.insertPart(RepairPart(UUID.randomUUID().toString(), jobId, productId, name, qty, cost))
        null
    }

    /** Puts a part back on the shelf at the cost it was taken at. */
    suspend fun removeRepairPart(partId: String) = db.withTransaction {
        val p = repairDao.getPart(partId) ?: return@withTransaction
        productDao.upsertBatch(StockBatch(UUID.randomUUID().toString(), p.productId, p.qty, p.qty, if (p.qty > 0) p.costTotal / p.qty else 0L, System.currentTimeMillis()))
        repairDao.deletePart(partId)
    }

    /**
     * Hands the phone back: the repair becomes a bill (so reports and the customer statement count it),
     * the payment goes into the chosen accounts, and anything unpaid becomes the customer's debt.
     */
    suspend fun deliverRepair(jobId: String, charge: Long, payments: List<PaymentInput>, warrantyDays: Int): String? = db.withTransaction {
        val job = repairDao.getJob(jobId) ?: return@withTransaction "Job not found."
        if (job.status == "DELIVERED") return@withTransaction "This job was already delivered."
        if (charge < 0) return@withTransaction "Enter the repair charge."
        val settings = settingsDao.get() ?: ShopSettings()
        val now = System.currentTimeMillis()
        val paidNow = payments.filter { it.amount > 0 }.sumOf { it.amount }
        val paid = job.advance + paidNow
        if (paid > charge) return@withTransaction "The payments are more than the repair charge."
        val due = charge - paid
        if (due > 0 && job.customerId == null) return@withTransaction "Add the customer's name to the job, because money is left to pay."
        val partsCost = repairDao.getParts(jobId).sumOf { it.costTotal } + job.outsideCost
        val saleId = UUID.randomUUID().toString()
        val label = "Repair #R-${job.jobNo.toString().padStart(3, '0')}: ${job.work.ifBlank { job.device }}"
        saleDao.upsertSale(
            Sale(saleId, settings.nextBillNumber, job.customerId, charge, 0L, 0L, charge, paid, due, null, "DONE", "", "Owner", now)
        )
        saleDao.insertLine(SaleLine(UUID.randomUUID().toString(), saleId, "SERVICE", null, label, 1, charge, charge, partsCost, null, job.imei, 0))
        for (p in payments.filter { it.amount > 0 }) {
            moneyDao.insertEntry(
                newEntry("SALE", p.accountId, p.amount, null, "Bill #${settings.nextBillNumber}", saleId)
                    .copy(contactId = job.customerId, refType = "SALE", refId = saleId)
            )
        }
        settingsDao.save(settings.copy(nextBillNumber = settings.nextBillNumber + 1))
        repairDao.upsertJob(job.copy(status = "DELIVERED", charge = charge, warrantyDays = warrantyDays, saleId = saleId, deliveredAt = now))
        null
    }

    val purchaseOrders: Flow<List<PurchaseOrder>> = purchaseDao.observeOrders()
    val poLines: Flow<List<PoLine>> = purchaseDao.observeAllPoLines()
    val purchases: Flow<List<Purchase>> = purchaseDao.observePurchases()
    val purchaseLines: Flow<List<PurchaseLine>> = purchaseDao.observeAllPurchaseLines()
    suspend fun getOrder(id: String): PurchaseOrder? = purchaseDao.getOrder(id)
    suspend fun getOrderLines(id: String): List<PoLine> = purchaseDao.getPoLines(id)
    fun observePurchaseOrder(id: String): Flow<PurchaseOrder?> = purchaseDao.observeOrder(id)

    /** Saves a purchase order (new or draft being edited). It changes no stock or money. */
    suspend fun savePurchaseOrder(
        existingId: String?,
        supplierId: String,
        orderDate: Long,
        expectedDate: Long?,
        note: String,
        lines: List<PoLineInput>,
        status: String,
    ): String? = db.withTransaction {
        if (lines.isEmpty()) return@withTransaction null
        val settings = settingsDao.get() ?: ShopSettings()
        val old = existingId?.let { purchaseDao.getOrder(it) }
        val id = existingId ?: UUID.randomUUID().toString()
        val poNo = old?.poNo ?: settings.nextPoNumber
        if (old == null) settingsDao.save(settings.copy(nextPoNumber = poNo + 1))
        purchaseDao.upsertOrder(
            PurchaseOrder(id, poNo, supplierId, status, orderDate, expectedDate, note.trim(), old?.createdAt ?: System.currentTimeMillis())
        )
        purchaseDao.deletePoLines(id)
        lines.forEach {
            purchaseDao.upsertPoLine(PoLine(UUID.randomUUID().toString(), id, it.productId, it.name, it.qty, it.unitCost))
        }
        id
    }

    suspend fun setOrderStatus(id: String, status: String) {
        purchaseDao.getOrder(id)?.let { purchaseDao.upsertOrder(it.copy(status = status)) }
    }

    /**
     * Goods arrive: stock goes up (each phone with its IMEI and cost), the supplier's bill is recorded,
     * any payment leaves the chosen account, and the unpaid part is added to what the shop owes.
     * Returns an error message, or null when saved.
     */
    suspend fun receiveGoods(
        poId: String?,
        supplierId: String,
        billNo: String,
        lines: List<ReceiveLine>,
        payAccountId: String?,
        paid: Long,
        payBy: Long?,
    ): String? = db.withTransaction {
        val real = lines.filter { it.qty > 0 }
        if (real.isEmpty()) return@withTransaction "Nothing was received."
        val allUnits = ArrayList<UnitInput>()
        for (l in real) {
            if (l.tracksImei) {
                val cleaned = l.imeis.map { it.trim() }.filter { it.isNotEmpty() }
                if (cleaned.size != l.qty) return@withTransaction "${l.name}: enter an IMEI for each of the ${l.qty} phones."
                allUnits.addAll(cleaned.map { UnitInput(it, "") })
            }
        }
        validateUnits(allUnits)?.let { return@withTransaction it }
        val total = real.sumOf { it.qty * it.unitCost }
        if (paid < 0 || paid > total) return@withTransaction "The payment is more than the bill."
        if (paid > 0 && payAccountId == null) return@withTransaction "Choose the account the payment comes from."

        val now = System.currentTimeMillis()
        val purchaseId = UUID.randomUUID().toString()
        for (l in real) {
            if (l.tracksImei) {
                l.imeis.map { it.trim() }.filter { it.isNotEmpty() }.forEach { insertUnit(l.productId, UnitInput(it, ""), l.unitCost, now) }
            } else {
                insertBatch(l.productId, l.qty, l.unitCost, now)
            }
            purchaseDao.insertPurchaseLine(PurchaseLine(UUID.randomUUID().toString(), purchaseId, l.productId, l.name, l.qty, l.unitCost))
            productDao.getProduct(l.productId)?.let { productDao.upsertProduct(it.copy(updatedAt = now)) }
        }
        purchaseDao.insertPurchase(Purchase(purchaseId, poId, supplierId, billNo.trim(), total, paid, total - paid, payBy, now))
        if (paid > 0 && payAccountId != null) {
            moneyDao.insertEntry(
                newEntry("PURCHASE_PAID", payAccountId, -paid, null, "Supplier bill ${billNo.trim()}", purchaseId)
                    .copy(contactId = supplierId, refType = "PURCHASE", refId = purchaseId)
            )
        }
        if (poId != null) {
            val poLines = purchaseDao.getPoLines(poId)
            for (pl in poLines) {
                val got = real.filter { it.poLineId == pl.id }.sumOf { it.qty }
                if (got > 0) purchaseDao.upsertPoLine(pl.copy(receivedQty = pl.receivedQty + got))
            }
            val after = purchaseDao.getPoLines(poId)
            val done = after.all { it.receivedQty >= it.qty }
            purchaseDao.getOrder(poId)?.let { purchaseDao.upsertOrder(it.copy(status = if (done) "RECEIVED" else "PART")) }
        }
        null
    }

    val sales: Flow<List<Sale>> = saleDao.observeSales()
    val allSaleLines: Flow<List<SaleLine>> = saleDao.observeAllLines()
    fun observeSale(id: String): Flow<Sale?> = saleDao.observeSale(id)
    fun observeSaleLines(id: String): Flow<List<SaleLine>> = saleDao.observeLines(id)

    /** contact id -> what they owe the shop (customers) or what the shop owes them (suppliers). */
    val contactBalances: Flow<Map<String, Long>> = combine(contacts, sales, entries, purchases) { people, bills, moves, bought ->
        people.associate { p ->
            val billed = bills.filter { it.customerId == p.id && it.status == "DONE" }.sumOf { it.due } +
                bought.filter { it.supplierId == p.id }.sumOf { it.due }
            val settled = moves.filter { it.contactId == p.id && it.voidedAt == null && (it.kind == "DEBT_PAID" || it.kind == "SUPPLIER_PAID") }
                .sumOf { kotlin.math.abs(it.amount) }
            p.id to (p.openingBalance + billed - settled)
        }
    }

    /**
 * Takes money from a customer for an old debt (not new income: the sale was counted already),
 * or pays a supplier what the shop owes.
 */
    suspend fun settleBalance(contactId: String, supplier: Boolean, accountId: String, amount: Long, newPromise: Long?, note: String): String? = db.withTransaction {
        if (amount <= 0) return@withTransaction "Enter the amount received."
        moneyDao.insertEntry(
            newEntry(
                if (supplier) "SUPPLIER_PAID" else "DEBT_PAID", accountId, if (supplier) -amount else amount, null, note,
                UUID.randomUUID().toString(),
            ).copy(contactId = contactId, refType = "CONTACT", refId = contactId)
        )
        contactDao.get(contactId)?.let { contactDao.upsert(it.copy(promiseDate = newPromise, updatedAt = System.currentTimeMillis())) }
        null
    }

    /**
     * Saves a bill: checks stock, freezes each line's cost, takes the payments into the chosen accounts,
     * and adds anything unpaid to the customer's balance. All or nothing.
     */
    suspend fun completeSale(
        lines: List<CartLine>,
        discount: Long,
        customerId: String?,
        payments: List<PaymentInput>,
        promiseDate: Long?,
    ): SaleResult = db.withTransaction {
        if (lines.isEmpty()) return@withTransaction SaleResult(null, "The cart is empty.")
        val settings = settingsDao.get() ?: ShopSettings()
        val subtotal = lines.sumOf { it.unitPrice * it.qty }
        if (discount < 0 || discount > subtotal) return@withTransaction SaleResult(null, "The discount is more than the bill.")
        val total = subtotal - discount
        val paid = payments.filter { it.amount > 0 }.sumOf { it.amount }
        if (paid > total) return@withTransaction SaleResult(null, "The payments are more than the bill total.")
        val due = total - paid
        if (due > 0 && customerId == null) return@withTransaction SaleResult(null, "Choose a customer, because some money is left to pay.")

        val now = System.currentTimeMillis()
        val saleId = UUID.randomUUID().toString()
        val saved = ArrayList<SaleLine>()
        for (l in lines) {
            var cost = 0L
            var unitId: String? = null
            var imei = ""
            if (l.kind == "PRODUCT") {
                if (l.tracksImei) {
                    val unit = l.unitId?.let { productDao.getUnit(it) }
                    if (unit == null || unit.status != "IN_STOCK") return@withTransaction SaleResult(null, "${l.name}: that phone is no longer in stock.")
                    productDao.upsertUnit(unit.copy(status = "SOLD"))
                    cost = unit.unitCost
                    unitId = unit.id
                    imei = unit.imei1
                } else {
                    val batches = productDao.batchesWithStock(l.productId ?: "")
                    val available = batches.sumOf { it.remaining }
                    if (available < l.qty) return@withTransaction SaleResult(null, "${l.name}: only $available in stock.")
                    // Average cost of what is on the shelf now, frozen on this line.
                    val value = batches.sumOf { it.remaining.toLong() * it.unitCost }
                    cost = value * l.qty / available
                    var need = l.qty
                    for (b in batches) {
                        if (need == 0) break
                        val take = minOf(need, b.remaining)
                        productDao.upsertBatch(b.copy(remaining = b.remaining - take))
                        need -= take
                    }
                }
            }
            saved.add(
                SaleLine(
                    id = UUID.randomUUID().toString(), saleId = saleId, kind = l.kind, productId = l.productId,
                    name = l.name, qty = l.qty, unitPrice = l.unitPrice, lineTotal = l.unitPrice * l.qty,
                    costTotal = cost, unitId = unitId, imei = imei, warrantyMonths = l.warrantyMonths,
                )
            )
        }
        val tax = if (settings.taxEnabled && settings.taxRateBp > 0) total * settings.taxRateBp / (10000 + settings.taxRateBp) else 0L
        saleDao.upsertSale(
            Sale(
                id = saleId, billNo = settings.nextBillNumber, customerId = customerId, subtotal = subtotal,
                discount = discount, taxAmount = tax, total = total, paid = paid, due = due, promiseDate = promiseDate,
                status = "DONE", cancelReason = "", staffName = "Owner", createdAt = now,
            )
        )
        saved.forEach { saleDao.insertLine(it) }
        for (p in payments.filter { it.amount > 0 }) {
            moneyDao.insertEntry(
                newEntry("SALE", p.accountId, p.amount, null, "Bill #${settings.nextBillNumber}", saleId)
                    .copy(contactId = customerId, refType = "SALE", refId = saleId)
            )
        }
        settingsDao.save(settings.copy(nextBillNumber = settings.nextBillNumber + 1))
        if (customerId != null && due > 0 && promiseDate != null) {
            contactDao.get(customerId)?.let { contactDao.upsert(it.copy(promiseDate = promiseDate, updatedAt = now)) }
        }
        SaleResult(saleId, null)
    }

    /** Cancels a bill: stock and money are put back, and the bill stays in the records with the reason. */
    suspend fun cancelSale(saleId: String, reason: String): String? = db.withTransaction {
        val sale = saleDao.getSale(saleId) ?: return@withTransaction "Bill not found."
        if (sale.status != "DONE") return@withTransaction "This bill is already cancelled."
        val now = System.currentTimeMillis()
        for (l in saleDao.getLines(saleId)) {
            if (l.kind != "PRODUCT") continue
            if (l.unitId != null) {
                productDao.getUnit(l.unitId)?.let { productDao.upsertUnit(it.copy(status = "IN_STOCK")) }
            } else if (l.productId != null) {
                // Put the items back as a batch at the cost they were sold at.
                productDao.upsertBatch(
                    StockBatch(UUID.randomUUID().toString(), l.productId, l.qty, l.qty, if (l.qty > 0) l.costTotal / l.qty else 0L, now)
                )
            }
        }
        moneyDao.voidGroup(saleId, now, "Bill #${sale.billNo} cancelled: ${reason.trim()}")
        saleDao.upsertSale(sale.copy(status = "CANCELLED", cancelReason = reason.trim(), cancelledAt = now))
        null
    }

    fun observeContact(id: String): Flow<Contact?> = contactDao.observe(id)
    suspend fun getContact(id: String): Contact? = contactDao.get(id)

    suspend fun saveService(existing: Service?, name: String, price: Long, note: String) {
        serviceDao.upsert(
            Service(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                price = price,
                note = note.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteService(id: String) = serviceDao.softDelete(id, System.currentTimeMillis())

    suspend fun saveContact(
        existing: Contact?,
        kind: String,
        name: String,
        phone: String,
        address: String,
        note: String,
        openingBalance: Long,
    ): String {
        val now = System.currentTimeMillis()
        val id = existing?.id ?: UUID.randomUUID().toString()
        contactDao.upsert(
            Contact(
                id = id, kind = kind, name = name.trim(), phone = phone.trim(), address = address.trim(),
                note = note.trim(), openingBalance = openingBalance,
                createdAt = existing?.createdAt ?: now, updatedAt = now,
                promiseDate = existing?.promiseDate,
            )
        )
        return id
    }

    suspend fun deleteContact(id: String) = contactDao.softDelete(id, System.currentTimeMillis())

    // Categories and their questions (Settings > Categories and questions)
    suspend fun saveCategory(existing: Category?, name: String, tracksImei: Boolean, hasWarranty: Boolean): String {
        val id = existing?.id ?: "cat_" + UUID.randomUUID().toString().take(8)
        val order = existing?.sortOrder ?: (System.currentTimeMillis() / 1000).toInt()
        categoryDao.upsertCategory(Category(id, name.trim(), tracksImei, hasWarranty, order))
        return id
    }

    /** Returns an error message, or null when the category was removed. */
    suspend fun deleteCategory(id: String): String? {
        if (categoryDao.productCount(id) > 0) return "This category still has products. Move or delete them first."
        categoryDao.softDeleteCategory(id, System.currentTimeMillis())
        return null
    }

    suspend fun saveField(existing: CategoryField?, categoryId: String, label: String, type: String, options: String, required: Boolean, order: Int) {
        categoryDao.upsertField(
            CategoryField(existing?.id ?: "f_" + UUID.randomUUID().toString().take(8), categoryId, label.trim(), type, options.trim(), required, order)
        )
    }

    suspend fun deleteField(id: String) = categoryDao.deleteField(id)

    val stock: Flow<Map<String, StockInfo>> =
        combine(productDao.observeUnitStock(), productDao.observeBatchStock()) { units, batches ->
            val result = HashMap<String, StockInfo>()
            for (row in units + batches) {
                val old = result[row.productId]
                result[row.productId] = if (old == null) {
                    StockInfo(row.qty, row.minCost, row.maxCost, row.totalCost)
                } else {
                    StockInfo(
                        old.qty + row.qty,
                        minOf(old.minCost, row.minCost),
                        maxOf(old.maxCost, row.maxCost),
                        old.totalCost + row.totalCost,
                    )
                }
            }
            result
        }

    /** The code the next new product will get, shown in the form so the owner can change it. */
    suspend fun suggestItemCode(): String {
        val s = settingsDao.get() ?: ShopSettings()
        return nextFreeCode(s.itemCodePrefix, s.nextItemNumber).first
    }

    suspend fun findByItemCode(code: String): Product? = productDao.findByCode(code.trim())

    private suspend fun nextFreeCode(prefix: String, start: Int): Pair<String, Int> {
        var n = start
        while (true) {
            val code = formatCode(prefix, n)
            if (productDao.codeTaken(code, "") == 0) return code to n
            n++
        }
    }

    suspend fun saveSettings(settings: ShopSettings) = settingsDao.save(settings)

    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) {
            categoryDao.insertCategories(SeedData.categories)
            categoryDao.insertFields(SeedData.fields)
        }
        if (moneyDao.accountCount() == 0) SeedData.accounts.forEach { moneyDao.upsertAccount(it) }
        if (moneyDao.typeCount() == 0) SeedData.entryTypes.forEach { moneyDao.upsertType(it) }
    }

    fun observeProduct(id: String): Flow<Product?> = productDao.observeProduct(id)
    suspend fun getProduct(id: String): Product? = productDao.getProduct(id)
    fun observeUnits(id: String): Flow<List<StockUnit>> = productDao.observeUnits(id)
    fun observeBatches(id: String): Flow<List<StockBatch>> = productDao.observeBatches(id)
    suspend fun productIdsByImei(q: String): List<String> = productDao.productIdsByImei(q)

    suspend fun deleteProduct(id: String) =
        productDao.softDelete(id, System.currentTimeMillis())

    /**
     * Creates or updates a product. For a new product the opening stock is saved as well.
     * Returns an error message for the user, or null when everything was saved.
     */
    suspend fun saveProduct(
        existingId: String?,
        categoryId: String,
        name: String,
        itemCode: String,
        barcode: String,
        sellPrice: Long,
        minPrice: Long,
        warrantyMonths: Int,
        lowStockLevel: Int,
        photoPath: String?,
        attributesJson: String,
        tracksImei: Boolean,
        openingUnits: List<UnitInput>,
        openingQty: Int,
        openingCost: Long,
    ): String? = db.withTransaction {
        val units = if (existingId == null && tracksImei) {
            openingUnits.filter { it.imei1.isNotBlank() }
        } else {
            emptyList()
        }
        val unitError = validateUnits(units)
        if (unitError != null) return@withTransaction unitError

        val now = System.currentTimeMillis()
        val old = existingId?.let { productDao.getProduct(it) }
        val id = existingId ?: UUID.randomUUID().toString()

        // Item code: blank or the suggested one takes the next number; anything else must be unique.
        var code = itemCode.trim().uppercase()
        val settings = settingsDao.get() ?: ShopSettings()
        if (code.any { it.isWhitespace() }) return@withTransaction "The item code cannot contain spaces."
        if (existingId == null) {
            val (suggested, number) = nextFreeCode(settings.itemCodePrefix, settings.nextItemNumber)
            if (code.isEmpty() || code == suggested) {
                code = suggested
                settingsDao.save(settings.copy(nextItemNumber = number + 1))
            }
        } else if (code.isEmpty()) {
            code = old?.itemCode ?: ""
        }
        if (productDao.codeTaken(code, id) > 0) return@withTransaction "The item code $code is already used by another product."
        productDao.upsertProduct(
            Product(
                id = id,
                categoryId = categoryId,
                name = name,
                itemCode = code,
                barcode = barcode,
                sellPrice = sellPrice,
                minPrice = minPrice,
                warrantyMonths = warrantyMonths,
                lowStockLevel = lowStockLevel,
                photoPath = photoPath,
                attributesJson = attributesJson,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
        )
        if (existingId == null) {
            if (tracksImei) {
                units.forEach { insertUnit(id, it, openingCost, now) }
            } else if (openingQty > 0) {
                insertBatch(id, openingQty, openingCost, now)
            }
        }
        null
    }

    /** Adds more stock to an existing product at its own buying price. Returns an error or null. */
    suspend fun addStock(
        productId: String,
        tracksImei: Boolean,
        units: List<UnitInput>,
        qty: Int,
        unitCost: Long,
    ): String? = db.withTransaction {
        val now = System.currentTimeMillis()
        if (tracksImei) {
            val list = units.filter { it.imei1.isNotBlank() }
            if (list.isEmpty()) return@withTransaction "Enter at least one IMEI."
            val error = validateUnits(list)
            if (error != null) return@withTransaction error
            list.forEach { insertUnit(productId, it, unitCost, now) }
        } else {
            if (qty <= 0) return@withTransaction "Enter how many items arrived."
            insertBatch(productId, qty, unitCost, now)
        }
        productDao.getProduct(productId)?.let {
            productDao.upsertProduct(it.copy(updatedAt = now))
        }
        null
    }

    private suspend fun validateUnits(units: List<UnitInput>): String? {
        val all = ArrayList<String>()
        for (u in units) {
            if (!isValidImei(u.imei1.trim())) return "IMEI ${u.imei1} must be 15 digits."
            all.add(u.imei1.trim())
            if (u.imei2.isNotBlank()) {
                if (!isValidImei(u.imei2.trim())) return "IMEI ${u.imei2} must be 15 digits."
                all.add(u.imei2.trim())
            }
        }
        if (all.size != all.toSet().size) return "The same IMEI is entered twice."
        for (imei in all) {
            if (productDao.imeiCount(imei) > 0) return "IMEI $imei is already in the app."
        }
        return null
    }

    private suspend fun insertUnit(productId: String, u: UnitInput, cost: Long, now: Long) {
        productDao.insertUnit(
            StockUnit(
                id = UUID.randomUUID().toString(),
                productId = productId,
                imei1 = u.imei1.trim(),
                imei2 = u.imei2.trim(),
                unitCost = cost,
                status = "IN_STOCK",
                note = "",
                createdAt = now,
            )
        )
    }

    private suspend fun insertBatch(productId: String, qty: Int, cost: Long, now: Long) {
        productDao.insertBatch(
            StockBatch(
                id = UUID.randomUUID().toString(),
                productId = productId,
                qty = qty,
                remaining = qty,
                unitCost = cost,
                createdAt = now,
            )
        )
    }
}
