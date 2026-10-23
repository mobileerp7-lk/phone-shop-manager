package app.phoneshop.manager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row, id = 1. Everything shop-specific lives here so the app stays a template. */
@Entity(tableName = "settings")
data class ShopSettings(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "",
    val address: String = "",
    val phone: String = "",
    val countryCode: String = "LK",
    val currencyCode: String = "LKR",
    val taxEnabled: Boolean = false,
    val taxNumber: String = "",
    val useDriveBackup: Boolean = false,
    val useSupabase: Boolean = false,
    val setupDone: Boolean = false,
    /** Item codes look like PH-0001. The prefix is the shop's choice. */
    val itemCodePrefix: String = "PH",
    val nextItemNumber: Int = 1,
    /** Colour theme id; blank means the app default. */
    val themeId: String = "",
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val tracksImei: Boolean,
    val hasWarranty: Boolean,
    val sortOrder: Int,
    val deletedAt: Long? = null,
)

/** A question asked when adding a product of this category. type: TEXT, NUMBER, CHOICE, YESNO. */
@Entity(tableName = "category_fields", indices = [Index("categoryId")])
data class CategoryField(
    @PrimaryKey val id: String,
    val categoryId: String,
    val label: String,
    val type: String,
    val options: String,
    val required: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "products",
    indices = [Index("categoryId"), Index(value = ["itemCode"], unique = true)],
)
data class Product(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    /** Short unique code printed on labels and typed at the till, e.g. PH-0001. */
    val itemCode: String,
    val barcode: String,
    /** Money is always whole minor units (cents / paisa), never floating point. */
    val sellPrice: Long,
    val minPrice: Long,
    val warrantyMonths: Int,
    val lowStockLevel: Int,
    val photoPath: String?,
    /** JSON object: category field id -> value. */
    val attributesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One physical phone tracked by IMEI, with the exact price paid for it. status: IN_STOCK or SOLD. */
@Entity(
    tableName = "stock_units",
    indices = [Index("productId"), Index(value = ["imei1"], unique = true)],
)
data class StockUnit(
    @PrimaryKey val id: String,
    val productId: String,
    val imei1: String,
    val imei2: String,
    val unitCost: Long,
    val status: String,
    val note: String,
    val createdAt: Long,
)

/** A purchase batch of items without IMEI, each batch with its own buying price. */
@Entity(tableName = "stock_batches", indices = [Index("productId")])
data class StockBatch(
    @PrimaryKey val id: String,
    val productId: String,
    val qty: Int,
    val remaining: Int,
    val unitCost: Long,
    val createdAt: Long,
)

data class StockRow(
    val productId: String,
    val qty: Int,
    val minCost: Long,
    val maxCost: Long,
    val totalCost: Long,
)

/** Something the shop does for a fee (repair, software install, unlocking ...). */
@Entity(tableName = "services")
data class Service(
    @PrimaryKey val id: String,
    val name: String,
    /** Usual charge in minor units; can be changed on each bill. */
    val price: Long,
    val note: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/** A customer or a supplier. kind is CUSTOMER or SUPPLIER. */
@Entity(tableName = "contacts", indices = [Index("kind")])
data class Contact(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val phone: String,
    val address: String,
    val note: String,
    /** Money already owed when the contact was added: customer owes the shop, or shop owes the supplier. */
    val openingBalance: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
