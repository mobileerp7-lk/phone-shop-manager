package app.phoneshop.manager.util

import app.phoneshop.manager.data.Repository

/**
 * Works out the true stock from the records that never change: what came in (batches and phones),
 * what went out (bill lines, repair parts, written-off stock) and what came back.
 * After two phones have shared their records this makes both show the same, correct numbers.
 * If more was sold than ever came in (two phones sold the last item), the stock shows 0.
 */
object StockReconcile {
    fun run(repo: Repository) {
        val db = repo.rawDb().openHelper.writableDatabase
        db.beginTransaction()
        try {
            // Phones (tracked by IMEI): sold when a live bill line holds them and they were not taken back.
            val sold = HashSet<String>()
            db.query(
                "SELECT l.unitId FROM sale_lines l JOIN sales s ON s.id = l.saleId " +
                    "WHERE l.unitId IS NOT NULL AND s.status = 'DONE' " +
                    "AND l.id NOT IN (SELECT lineId FROM sale_returns WHERE restockedCost > 0)",
            ).use { while (it.moveToNext()) sold.add(it.getString(0)) }
            val unitFix = ArrayList<Pair<String, String>>()
            db.query("SELECT id, status FROM stock_units").use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val status = it.getString(1)
                    if (status == "WRITTEN_OFF") continue
                    val want = if (id in sold) "SOLD" else "IN_STOCK"
                    if (want != status) unitFix.add(id to want)
                }
            }
            for ((id, want) in unitFix) db.execSQL("UPDATE stock_units SET status = ? WHERE id = ?", arrayOf<Any>(want, id))

            // Items without IMEI: everything that came in minus everything that went out.
            val imeiProducts = HashSet<String>()
            db.query("SELECT DISTINCT productId FROM stock_units").use { while (it.moveToNext()) imeiProducts.add(it.getString(0)) }
            val came = HashMap<String, Long>()
            db.query("SELECT productId, SUM(qty) FROM stock_batches WHERE id NOT LIKE 'partback-%' GROUP BY productId")
                .use { while (it.moveToNext()) came[it.getString(0)] = it.getLong(1) }
            val went = HashMap<String, Long>()
            fun addWent(sql: String) = db.query(sql).use { while (it.moveToNext()) went.merge(it.getString(0), it.getLong(1), Long::plus) }
            addWent("SELECT productId, SUM(qty) FROM sale_lines WHERE kind = 'PRODUCT' AND unitId IS NULL AND productId IS NOT NULL GROUP BY productId")
            addWent("SELECT productId, SUM(qty) FROM repair_parts GROUP BY productId")
            addWent("SELECT productId, SUM(qty) FROM stock_adjustments GROUP BY productId")

            val batchFix = ArrayList<Pair<String, Int>>()
            for ((productId, total) in came) {
                if (productId in imeiProducts) continue
                var left = (total - (went[productId] ?: 0L)).coerceAtLeast(0L)
                db.query("SELECT id, qty, remaining FROM stock_batches WHERE productId = ? ORDER BY createdAt DESC, id", arrayOf<Any>(productId)).use {
                    while (it.moveToNext()) {
                        val id = it.getString(0)
                        val qty = it.getLong(1)
                        val rem = it.getLong(2)
                        val want = if (id.startsWith("partback-")) 0L else minOf(qty, left)
                        if (!id.startsWith("partback-")) left -= want
                        if (want != rem) batchFix.add(id to want.toInt())
                    }
                }
            }
            for ((id, want) in batchFix) db.execSQL("UPDATE stock_batches SET remaining = ? WHERE id = ?", arrayOf<Any>(want, id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
