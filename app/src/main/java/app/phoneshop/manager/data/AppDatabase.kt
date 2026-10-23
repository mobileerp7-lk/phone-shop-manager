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

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertCategories(items: List<Category>)

    @Insert
    suspend fun insertFields(items: List<CategoryField>)
}

@Dao
interface ProductDao {
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

// exportSchema stays off until Step 2; before any public release it must be turned on
// and every schema change must ship a Room migration so shop data is never lost.
@Database(
    entities = [
        ShopSettings::class,
        Category::class,
        CategoryField::class,
        Product::class,
        StockUnit::class,
        StockBatch::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "phoneshop.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds item codes. Products saved before this get PH-0001, PH-0002 ... so nothing is lost. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN itemCodePrefix TEXT NOT NULL DEFAULT 'PH'")
                db.execSQL("ALTER TABLE settings ADD COLUMN nextItemNumber INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE products ADD COLUMN itemCode TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE products SET itemCode = 'PH-' || substr('0000' || " +
                        "(SELECT COUNT(*) FROM products p2 WHERE p2.createdAt < products.createdAt " +
                        "OR (p2.createdAt = products.createdAt AND p2.id <= products.id)), -4)"
                )
                db.execSQL("UPDATE settings SET nextItemNumber = (SELECT COUNT(*) + 1 FROM products)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_itemCode` ON `products` (`itemCode`)")
            }
        }
    }
}
