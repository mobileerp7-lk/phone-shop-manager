package app.phoneshop.manager.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

data class StockInfo(val qty: Int, val minCost: Long, val maxCost: Long, val totalCost: Long)

/** An account with its current balance and what moved today. */
data class AccountView(val account: Account, val balance: Long, val todayIn: Long, val todayOut: Long)

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
