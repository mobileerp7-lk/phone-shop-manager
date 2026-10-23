package app.phoneshop.manager.ui

import app.phoneshop.manager.data.MoneyEntry
import app.phoneshop.manager.data.Sale
import app.phoneshop.manager.data.SaleReturn
import app.phoneshop.manager.data.StockAdjustment
import app.phoneshop.manager.data.SaleLine
import java.time.LocalDate
import java.time.ZoneId

/** Everything the report screens show for one stretch of time. Money is in minor units. */
data class ReportData(
    val bills: Int,
    val productSales: Long,
    val productCost: Long,
    val productItems: Int,
    val repairIncome: Long,
    val repairCost: Long,
    val repairJobs: Int,
    val serviceIncome: Long,
    val serviceCost: Long,
    val serviceCount: Int,
    val discounts: Long,
    val otherIncome: Long,
    val expenses: Long,
    val expenseCount: Int,
    val expenseByType: List<Pair<String, Long>>,
    val newDebt: Long,
    val debtCollected: Long,
    val supplierPaid: Long,
    val writeOffs: Long,
    val refunds: Long,
    /** name, quantity, profit */
    val topItems: List<Triple<String, Int, Long>>,
) {
    val productProfit get() = productSales - productCost
    val repairProfit get() = repairIncome - repairCost
    val serviceProfit get() = serviceIncome - serviceCost
    val revenue get() = productSales + repairIncome + serviceIncome
    val netProfit get() = productProfit + repairProfit + serviceProfit - discounts + otherIncome - expenses - writeOffs
}

object ReportMath {
    fun startOf(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun isRepair(l: SaleLine) = l.kind == "SERVICE" && l.name.startsWith("Repair #")

    /** Works out a report for from (included) up to to (excluded). Cancelled bills and entries are left out. */
    fun build(
        from: Long,
        to: Long,
        sales: List<Sale>,
        lines: List<SaleLine>,
        entries: List<MoneyEntry>,
        typeNames: Map<String, String>,
        returns: List<SaleReturn> = emptyList(),
        adjustments: List<StockAdjustment> = emptyList(),
    ): ReportData {
        val backs = returns.filter { it.createdAt in from until to }
        val losses = adjustments.filter { it.createdAt in from until to }
        val bills = sales.filter { it.status == "DONE" && it.createdAt in from until to }
        val ids = bills.map { it.id }.toSet()
        val ls = lines.filter { it.saleId in ids }
        val products = ls.filter { it.kind == "PRODUCT" }
        val repairs = ls.filter { isRepair(it) }
        val services = ls.filter { it.kind == "SERVICE" && !isRepair(it) }
        val live = entries.filter { it.voidedAt == null && it.entryDate in from until to }
        val exp = live.filter { it.kind == "EXPENSE" }
        val byType = exp.groupBy { typeNames[it.typeId] ?: "Other" }
            .map { it.key to -it.value.sumOf { e -> e.amount } }
            .sortedByDescending { it.second }
        val top = ls.filter { it.kind == "PRODUCT" || !isRepair(it) }
            .groupBy { it.name }
            .map { (name, group) -> Triple(name, group.sumOf { it.qty }, group.sumOf { it.lineTotal - it.costTotal }) }
            .sortedByDescending { it.second }
            .take(5)
        return ReportData(
            bills = bills.size,
            productSales = products.sumOf { it.lineTotal } - backs.sumOf { it.refund },
            productCost = products.sumOf { it.costTotal } - backs.sumOf { it.restockedCost },
            productItems = products.sumOf { it.qty },
            repairIncome = repairs.sumOf { it.lineTotal },
            repairCost = repairs.sumOf { it.costTotal },
            repairJobs = repairs.size,
            serviceIncome = services.sumOf { it.lineTotal },
            serviceCost = services.sumOf { it.costTotal },
            serviceCount = services.sumOf { it.qty },
            discounts = bills.sumOf { it.discount },
            otherIncome = live.filter { it.kind == "INCOME" }.sumOf { it.amount },
            expenses = -exp.sumOf { it.amount },
            expenseCount = exp.size,
            expenseByType = byType,
            newDebt = bills.sumOf { it.due },
            debtCollected = live.filter { it.kind == "DEBT_PAID" }.sumOf { it.amount },
            supplierPaid = -live.filter { it.kind == "SUPPLIER_PAID" || it.kind == "PURCHASE_PAID" }.sumOf { it.amount },
            writeOffs = losses.sumOf { it.costTotal },
            refunds = backs.sumOf { it.refund },
            topItems = top,
        )
    }
}
