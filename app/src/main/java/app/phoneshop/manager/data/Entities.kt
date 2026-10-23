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
    val nextBillNumber: Int = 1001,
    /** Tax rate in hundredths of a percent (1800 = 18%). Prices already include tax. */
    val taxRateBp: Int = 0,
    val nextPoNumber: Int = 1,
    val nextJobNumber: Int = 1,
    val nextQuoteNumber: Int = 1,
    /** A folder the owner picked (for example in Google Drive) for the automatic daily backup. */
    val backupTreeUri: String = "",
    val lastBackupAt: Long = 0L,
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
    /** When the customer promised to pay what they owe. */
    val promiseDate: Long? = null,
)

/** A place the shop's money sits: cash drawer, bank account, mobile wallet. type: CASH, BANK, WALLET. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    /** For example the last digits of a bank account. */
    val detail: String,
    val openingBalance: Long,
    val sortOrder: Int,
    val deletedAt: Long? = null,
)

/** A reason for money going out (rent, salary ...) or coming in (commission ...). kind: EXPENSE or INCOME. */
@Entity(tableName = "entry_types")
data class EntryType(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sortOrder: Int,
    val deletedAt: Long? = null,
)

/**
 * One movement on one account. amount is signed: plus puts money in, minus takes it out.
 * kind: EXPENSE, INCOME, TRANSFER, OWNER_IN, OWNER_OUT (later: SALE, DEBT_PAID, SUPPLIER_PAID).
 * A transfer is two entries that share a groupId. Entries are never deleted, only voided.
 */
@Entity(
    tableName = "money_entries",
    indices = [Index("accountId"), Index("entryDate"), Index("groupId")],
)
data class MoneyEntry(
    @PrimaryKey val id: String,
    val kind: String,
    val accountId: String,
    val amount: Long,
    val typeId: String?,
    val contactId: String?,
    val refType: String,
    val refId: String,
    val groupId: String,
    val note: String,
    val entryDate: Long,
    val createdAt: Long,
    val voidedAt: Long? = null,
    val voidReason: String = "",
)

/** A finished bill. status: DONE or CANCELLED. All money is in minor units. */
@Entity(tableName = "sales", indices = [Index("customerId"), Index("createdAt")])
data class Sale(
    @PrimaryKey val id: String,
    val billNo: Int,
    val customerId: String?,
    val subtotal: Long,
    val discount: Long,
    val taxAmount: Long,
    val total: Long,
    /** Paid at the time of the sale. */
    val paid: Long,
    /** total - paid: added to the customer's balance. */
    val due: Long,
    val promiseDate: Long?,
    val status: String,
    val cancelReason: String,
    val staffName: String,
    val createdAt: Long,
    val cancelledAt: Long? = null,
)

/** One line on a bill. costTotal is frozen when sold and never recalculated. kind: PRODUCT or SERVICE. */
@Entity(tableName = "sale_lines", indices = [Index("saleId")])
data class SaleLine(
    @PrimaryKey val id: String,
    val saleId: String,
    val kind: String,
    val productId: String?,
    val name: String,
    val qty: Int,
    val unitPrice: Long,
    val lineTotal: Long,
    val costTotal: Long,
    val unitId: String?,
    val imei: String,
    val warrantyMonths: Int,
)

/** An order sent to a supplier. It changes no stock or money until goods arrive. status: DRAFT, SENT, PART, RECEIVED, CANCELLED. */
@Entity(tableName = "purchase_orders", indices = [Index("supplierId")])
data class PurchaseOrder(
    @PrimaryKey val id: String,
    val poNo: Int,
    val supplierId: String,
    val status: String,
    val orderDate: Long,
    val expectedDate: Long?,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "po_lines", indices = [Index("poId")])
data class PoLine(
    @PrimaryKey val id: String,
    val poId: String,
    val productId: String,
    val name: String,
    val qty: Int,
    val unitCost: Long,
    val receivedQty: Int = 0,
)

/** Goods that arrived, with the supplier's bill. due is added to what the shop owes the supplier. */
@Entity(tableName = "purchases", indices = [Index("supplierId"), Index("poId")])
data class Purchase(
    @PrimaryKey val id: String,
    val poId: String?,
    val supplierId: String,
    val billNo: String,
    val total: Long,
    val paid: Long,
    val due: Long,
    val payBy: Long?,
    val createdAt: Long,
)

@Entity(tableName = "purchase_lines", indices = [Index("purchaseId")])
data class PurchaseLine(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val productId: String,
    val name: String,
    val qty: Int,
    val unitCost: Long,
)

/**
 * A phone brought in for repair. status: RECEIVED, CHECKING, WAITING_PARTS, REPAIRING, READY, DELIVERED, CANCELLED.
 * When delivered it becomes a bill (saleId) so reports and the customer statement count it.
 */
@Entity(tableName = "repair_jobs", indices = [Index("status"), Index("customerId")])
data class RepairJob(
    @PrimaryKey val id: String,
    val jobNo: Int,
    val customerId: String?,
    val customerName: String,
    val phone: String,
    val device: String,
    val imei: String,
    val lockNote: String,
    val cameWith: String,
    val work: String,
    val problem: String,
    val estimate: Long,
    val advance: Long,
    val readyBy: Long?,
    val status: String,
    /** Final charge, set when delivered. */
    val charge: Long,
    val outsideCost: Long,
    val warrantyDays: Int,
    val assignedTo: String,
    val saleId: String?,
    val createdAt: Long,
    val deliveredAt: Long? = null,
)

/** A part taken from stock for a repair. costTotal is frozen when added. */
@Entity(tableName = "repair_parts", indices = [Index("jobId")])
data class RepairPart(
    @PrimaryKey val id: String,
    val jobId: String,
    val productId: String,
    val name: String,
    val qty: Int,
    val costTotal: Long,
)

/** The end-of-day cash count. id is the date, like 2026-10-23. */
@Entity(tableName = "day_closes")
data class DayClose(
    @PrimaryKey val id: String,
    val expectedCash: Long,
    val countedCash: Long,
    val difference: Long,
    val reason: String,
    val leftInDrawer: Long,
    val closedAt: Long,
)

/** Items a customer brought back from a bill. refundMode: CASH (paid out of an account) or BALANCE (reduces what they owe). */
@Entity(tableName = "sale_returns", indices = [Index("saleId"), Index("createdAt")])
data class SaleReturn(
    @PrimaryKey val id: String,
    val saleId: String,
    val lineId: String,
    val name: String,
    val qty: Int,
    val refund: Long,
    /** Cost of the goods put back on the shelf; 0 when they were not restocked (damaged). */
    val restockedCost: Long,
    val refundMode: String,
    val customerId: String?,
    val reason: String,
    val createdAt: Long,
)

/** Stock lost, damaged or corrected. costTotal is what the written-off goods had cost. */
@Entity(tableName = "stock_adjustments", indices = [Index("productId"), Index("createdAt")])
data class StockAdjustment(
    @PrimaryKey val id: String,
    val productId: String,
    val name: String,
    val qty: Int,
    val costTotal: Long,
    val reason: String,
    val createdAt: Long,
)

/** A saved cart. kind: QUOTE (a price offer) or HELD (a sale put on hold). linesJson holds the cart lines. */
@Entity(tableName = "quotes", indices = [Index("kind")])
data class Quote(
    @PrimaryKey val id: String,
    val kind: String,
    val number: Int,
    val customerId: String?,
    val total: Long,
    val discount: Long,
    val linesJson: String,
    val createdAt: Long,
)

/** A person who uses the app. role: OWNER, MANAGER or CASHIER. A blank pinHash means no PIN is needed. */
@Entity(tableName = "staff")
data class Staff(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val pinHash: String,
    val salt: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/** Who did what and when. Nothing is ever secretly deleted. */
@Entity(tableName = "activity_log", indices = [Index("createdAt")])
data class ActivityLog(
    @PrimaryKey val id: String,
    val who: String,
    val text: String,
    val createdAt: Long,
)
