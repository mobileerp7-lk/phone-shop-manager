package app.phoneshop.manager.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<ShopSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: ShopSettings)

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): ShopSettings?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM category_fields ORDER BY sortOrder")
    fun observeFields(): Flow<List<CategoryField>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertField(field: CategoryField)

    @Query("DELETE FROM category_fields WHERE id = :id")
    suspend fun deleteField(id: String)

    @Query("UPDATE categories SET deletedAt = :time WHERE id = :id")
    suspend fun softDeleteCategory(id: String, time: Long)

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :id AND deletedAt IS NULL")
    suspend fun productCount(id: String): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertCategories(items: List<Category>)

    @Insert
    suspend fun insertFields(items: List<CategoryField>)
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<Service>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(service: Service)

    @Query("UPDATE services SET deletedAt = :time WHERE id = :id")
    suspend fun softDelete(id: String, time: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observe(id: String): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: Contact)

    @Query("UPDATE contacts SET deletedAt = :time, updatedAt = :time WHERE id = :id")
    suspend fun softDelete(id: String, time: Long)
}

@Dao
interface MoneyDao {
    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccount(id: String): Account?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: Account)

    @Query("UPDATE accounts SET deletedAt = :time WHERE id = :id")
    suspend fun softDeleteAccount(id: String, time: Long)

    @Query("SELECT COUNT(*) FROM money_entries WHERE accountId = :id AND voidedAt IS NULL")
    suspend fun entryCountForAccount(id: String): Int

    @Query("SELECT * FROM entry_types WHERE deletedAt IS NULL ORDER BY kind, sortOrder, name")
    fun observeTypes(): Flow<List<EntryType>>

    @Query("SELECT COUNT(*) FROM entry_types")
    suspend fun typeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertType(type: EntryType)

    @Query("UPDATE entry_types SET deletedAt = :time WHERE id = :id")
    suspend fun softDeleteType(id: String, time: Long)

    @Query("SELECT * FROM money_entries ORDER BY entryDate DESC, createdAt DESC")
    fun observeEntries(): Flow<List<MoneyEntry>>

    @Insert
    suspend fun insertEntry(entry: MoneyEntry)

    @Query("UPDATE money_entries SET voidedAt = :time, voidReason = :reason WHERE groupId = :groupId AND voidedAt IS NULL")
    suspend fun voidGroup(groupId: String, time: Long, reason: String)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeStaff(): Flow<List<Staff>>

    @Query("SELECT COUNT(*) FROM staff")
    suspend fun staffCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaff(s: Staff)

    @Query("SELECT * FROM activity_log ORDER BY createdAt DESC LIMIT 300")
    fun observeLog(): Flow<List<ActivityLog>>

    @Insert
    suspend fun insertLog(l: ActivityLog)
}

@Dao
interface ExtrasDao {
    @Query("SELECT * FROM sale_returns ORDER BY createdAt DESC")
    fun observeReturns(): Flow<List<SaleReturn>>

    @Insert
    suspend fun insertReturn(r: SaleReturn)

    @Query("SELECT COALESCE(SUM(qty), 0) FROM sale_returns WHERE lineId = :lineId")
    suspend fun returnedQty(lineId: String): Int

    @Query("SELECT * FROM stock_adjustments ORDER BY createdAt DESC")
    fun observeAdjustments(): Flow<List<StockAdjustment>>

    @Insert
    suspend fun insertAdjustment(a: StockAdjustment)

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun observeQuotes(): Flow<List<Quote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuote(q: Quote)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: String)
}

@Dao
interface DayCloseDao {
    @Query("SELECT * FROM day_closes")
    fun observe(): Flow<List<DayClose>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(close: DayClose)

    @Query("DELETE FROM day_closes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RepairDao {
    @Query("SELECT * FROM repair_jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<RepairJob>>

    @Query("SELECT * FROM repair_jobs WHERE id = :id")
    fun observeJob(id: String): Flow<RepairJob?>

    @Query("SELECT * FROM repair_jobs WHERE id = :id")
    suspend fun getJob(id: String): RepairJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: RepairJob)

    @Query("SELECT * FROM repair_parts")
    fun observeParts(): Flow<List<RepairPart>>

    @Query("SELECT * FROM repair_parts WHERE id = :id")
    suspend fun getPart(id: String): RepairPart?

    @Query("SELECT * FROM repair_parts WHERE jobId = :id")
    suspend fun getParts(id: String): List<RepairPart>

    @Insert
    suspend fun insertPart(part: RepairPart)

    @Query("DELETE FROM repair_parts WHERE id = :id")
    suspend fun deletePart(id: String)
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<PurchaseOrder>>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    fun observeOrder(id: String): Flow<PurchaseOrder?>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun getOrder(id: String): PurchaseOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: PurchaseOrder)

    @Query("SELECT * FROM po_lines")
    fun observeAllPoLines(): Flow<List<PoLine>>

    @Query("SELECT * FROM po_lines WHERE poId = :id")
    suspend fun getPoLines(id: String): List<PoLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoLine(line: PoLine)

    @Query("DELETE FROM po_lines WHERE poId = :id")
    suspend fun deletePoLines(id: String)

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    fun observePurchases(): Flow<List<Purchase>>

    @Insert
    suspend fun insertPurchase(purchase: Purchase)

    @Query("SELECT * FROM purchase_lines")
    fun observeAllPurchaseLines(): Flow<List<PurchaseLine>>

    @Insert
    suspend fun insertPurchaseLine(line: PurchaseLine)
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeSale(id: String): Flow<Sale?>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: String): Sale?

    @Query("SELECT * FROM sale_lines WHERE saleId = :id")
    fun observeLines(id: String): Flow<List<SaleLine>>

    @Query("SELECT * FROM sale_lines WHERE saleId = :id")
    suspend fun getLines(id: String): List<SaleLine>

    @Query("SELECT * FROM sale_lines")
    fun observeAllLines(): Flow<List<SaleLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSale(sale: Sale)

    @Insert
    suspend fun insertLine(line: SaleLine)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM stock_units WHERE id = :id")
    suspend fun getUnit(id: String): StockUnit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnit(unit: StockUnit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(batch: StockBatch)

    @Query("SELECT * FROM stock_batches WHERE productId = :id AND remaining > 0 ORDER BY createdAt")
    suspend fun batchesWithStock(id: String): List<StockBatch>

    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProduct(id: String): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: String): Product?

    @Query("SELECT COUNT(*) FROM products WHERE UPPER(itemCode) = UPPER(:code) AND id != :excludeId")
    suspend fun codeTaken(code: String, excludeId: String): Int

    @Query("SELECT * FROM products WHERE UPPER(itemCode) = UPPER(:code) AND deletedAt IS NULL LIMIT 1")
    suspend fun findByCode(code: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: Product)

    @Query("UPDATE products SET deletedAt = :time, updatedAt = :time WHERE id = :id")
    suspend fun softDelete(id: String, time: Long)

    @Insert
    suspend fun insertUnit(unit: StockUnit)

    @Insert
    suspend fun insertBatch(batch: StockBatch)

    @Query("SELECT COUNT(*) FROM stock_units WHERE imei1 = :imei OR imei2 = :imei")
    suspend fun imeiCount(imei: String): Int

    @Query(
        "SELECT DISTINCT productId FROM stock_units " +
            "WHERE imei1 LIKE '%' || :q || '%' OR imei2 LIKE '%' || :q || '%'"
    )
    suspend fun productIdsByImei(q: String): List<String>

    @Query("SELECT * FROM stock_units WHERE productId = :id ORDER BY createdAt DESC")
    fun observeUnits(id: String): Flow<List<StockUnit>>

    @Query("SELECT * FROM stock_batches WHERE productId = :id ORDER BY createdAt DESC")
    fun observeBatches(id: String): Flow<List<StockBatch>>

    @Query(
        "SELECT productId, COUNT(*) AS qty, MIN(unitCost) AS minCost, MAX(unitCost) AS maxCost, " +
            "SUM(unitCost) AS totalCost FROM stock_units WHERE status = 'IN_STOCK' GROUP BY productId"
    )
    fun observeUnitStock(): Flow<List<StockRow>>

    @Query(
        "SELECT productId, SUM(remaining) AS qty, MIN(unitCost) AS minCost, MAX(unitCost) AS maxCost, " +
            "SUM(remaining * unitCost) AS totalCost FROM stock_batches WHERE remaining > 0 GROUP BY productId"
    )
    fun observeBatchStock(): Flow<List<StockRow>>
}

// Version 10 is the first public layout (schemas/ holds the exported copy).
// Every later change to a table must bump the version and add a Migration.
@Database(
    entities = [
        ShopSettings::class,
        Category::class,
        CategoryField::class,
        Product::class,
        StockUnit::class,
        StockBatch::class,
        Service::class,
        Contact::class,
        Account::class,
        EntryType::class,
        MoneyEntry::class,
        Sale::class,
        SaleLine::class,
        PurchaseOrder::class,
        PoLine::class,
        Purchase::class,
        PurchaseLine::class,
        RepairJob::class,
        RepairPart::class,
        DayClose::class,
        SaleReturn::class,
        StockAdjustment::class,
        Quote::class,
        Staff::class,
        ActivityLog::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun serviceDao(): ServiceDao
    abstract fun contactDao(): ContactDao
    abstract fun moneyDao(): MoneyDao
    abstract fun saleDao(): SaleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun repairDao(): RepairDao
    abstract fun dayCloseDao(): DayCloseDao
    abstract fun extrasDao(): ExtrasDao
    abstract fun staffDao(): StaffDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "phoneshop.db")
                // Only the early test builds (versions 1 to 9) may start fresh. Room refuses a version that is both here and in a Migration. From version 10 on,
                // every change to the tables must ship a real migration so shop data is never lost.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9)
                .build()
    }
}
