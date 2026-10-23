package app.phoneshop.manager.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import app.phoneshop.manager.data.Repository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Shares the shop's records between phones through the shop's own Supabase project.
 *
 * Every record of the app is one row online. A phone sends the rows it changed and fetches the rows
 * others changed. Changes made offline wait and are sent when the internet is back. When two phones
 * change the same record, the newer change wins. Stock counts are then recalculated from the records
 * themselves (see StockReconcile), so two phones can never leave the stock wrong.
 */
object SyncEngine {
    /** Everything is shared except the settings page and the staff list, which stay per phone for now. */
    val TABLES = listOf(
        "categories", "category_fields", "products", "stock_units", "stock_batches", "services", "contacts",
        "accounts", "entry_types", "money_entries", "sales", "sale_lines", "purchase_orders", "po_lines",
        "purchases", "purchase_lines", "repair_jobs", "repair_parts", "day_closes", "sale_returns",
        "stock_adjustments", "quotes", "activity_log",
    )

    private const val DELETED = "DELETED"
    private val lock = Any()

    private class Meta(context: Context) : SQLiteOpenHelper(context, "sync.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, hash TEXT NOT NULL, pending INTEGER NOT NULL DEFAULT 0)")
        }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Same text for the same record, whatever order the columns come in. */
    private fun canonical(o: JSONObject): String =
        o.keys().asSequence().sorted().joinToString("|") { k -> "$k=${if (o.isNull(k)) "~" else o.get(k).toString()}" }

    private fun rowJson(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> o.put(name, c.getString(i))
                else -> {}
            }
        }
        return o
    }

    private fun writeRow(db: SupportSQLiteDatabase, table: String, data: JSONObject) {
        val cv = ContentValues()
        for (k in data.keys()) {
            val v = data.get(k)
            when {
                data.isNull(k) -> cv.putNull(k)
                v is Int -> cv.put(k, v.toLong())
                v is Long -> cv.put(k, v)
                v is Double -> if (v == Math.floor(v) && Math.abs(v) < 9e15) cv.put(k, v.toLong()) else cv.put(k, v)
                v is Boolean -> cv.put(k, if (v) 1 else 0)
                else -> cv.put(k, v.toString())
            }
        }
        try {
            db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv)
        } catch (e: SQLiteConstraintException) {
            // Two phones made the same item code at once: keep both products, make the code unique.
            if (table == "products") {
                cv.put("itemCode", cv.getAsString("itemCode") + "-" + cv.getAsString("id").take(4).uppercase())
                runCatching { db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv) }
            }
            // Other clashes (for example the same IMEI twice) keep the local record.
        }
    }

    private fun clearMeta(context: Context) { Meta(context).writableDatabase.execSQL("DELETE FROM meta") }

    /** Online shop for the logged-in user: the one they belong to, or a new one. Returns the shop id. */
    fun ensureShop(c: CloudBackup.Config, s: CloudBackup.Session, name: String): String {
        val auth = mapOf("apikey" to c.key, "Authorization" to "Bearer ${s.token}", "Content-Type" to "application/json")
        val (code, bytes) = CloudBackup.call("GET", "${c.url}/rest/v1/shop_members?select=shop_id&limit=1", auth)
        if (code == 200) {
            val arr = JSONArray(String(bytes))
            if (arr.length() > 0) return arr.getJSONObject(0).getString("shop_id")
        } else if (code == 404 || code == 400) {
            error("The shop tables are missing in Supabase. Run the setup script first.")
        }
        val (c2, b2) = CloudBackup.call("POST", "${c.url}/rest/v1/rpc/create_shop", auth, JSONObject().put("p_name", name).toString().toByteArray())
        if (c2 !in 200..299) error("Could not create the online shop (code $c2). Run the setup script first.")
        return String(b2).trim().trim('"')
    }

    /** First phone: shares everything it has. */
    fun startAsMain(context: Context, c: CloudBackup.Config, shopName: String): String? = runCatching {
        val s = CloudBackup.signIn(c)
        val shop = ensureShop(c, s, shopName)
        Prefs.setShopId(context, shop)
        Prefs.setLastSeq(context, 0L)
        clearMeta(context)
        null as String?
    }.getOrElse { it.message ?: "Could not connect." }

    /** Another phone: throws away what it has and takes the shop's data. [slot] keeps bill numbers apart. */
    fun joinShop(context: Context, repo: Repository, c: CloudBackup.Config, slot: Int): String? = runCatching {
        val s = CloudBackup.signIn(c)
        val shop = ensureShop(c, s, "")
        val db = repo.rawDb().openHelper.writableDatabase
        db.beginTransaction()
        try {
            for (t in TABLES) db.execSQL("DELETE FROM $t")
            val base = slot * 100000
            db.execSQL(
                "UPDATE settings SET nextBillNumber = ?, nextPoNumber = ?, nextJobNumber = ?, nextQuoteNumber = ?, nextItemNumber = ?",
                arrayOf<Any>(base + 1001, base + 1, base + 1, base + 1, base + 1),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Prefs.setShopId(context, shop)
        Prefs.setLastSeq(context, 0L)
        clearMeta(context)
        null as String?
    }.getOrElse { it.message ?: "Could not connect." }

    fun stop(context: Context) { Prefs.setShopId(context, "") }

    /** One round: send my changes, fetch others'. Returns null when fine, or a short message. */
    fun syncOnce(context: Context, repo: Repository): String? = synchronized(lock) {
        val shop = Prefs.shopId(context)
        val cfg = CloudBackup.load(context)
        if (shop.isBlank() || !cfg.ready) return null
        runCatching {
            val session = CloudBackup.signIn(cfg)
            val headers = mapOf("apikey" to cfg.key, "Authorization" to "Bearer ${session.token}", "Content-Type" to "application/json")
            val db = repo.rawDb().openHelper.writableDatabase
            val meta = Meta(context).writableDatabase
            val known = HashMap<String, Pair<String, Long>>()
            meta.rawQuery("SELECT k, hash, pending FROM meta", null).use { while (it.moveToNext()) known[it.getString(0)] = it.getString(1) to it.getLong(2) }
            val now = System.currentTimeMillis()

            // 1. Find what changed on this phone.
            class Out(val key: String, val tbl: String, val id: String, val data: JSONObject?, val hash: String, val at: Long)
            val outs = ArrayList<Out>()
            val seen = HashSet<String>()
            for (t in TABLES) {
                db.query("SELECT * FROM $t").use { c ->
                    val idCol = c.getColumnIndexOrThrow("id")
                    while (c.moveToNext()) {
                        val o = rowJson(c)
                        val id = c.getString(idCol)
                        val key = "$t:$id"
                        seen.add(key)
                        val h = sha(canonical(o))
                        val old = known[key]
                        if (old == null || old.first != h) {
                            val at = if (old != null && old.second > 0) old.second else now
                            outs.add(Out(key, t, id, o, h, at))
                            if (old != null && old.second == 0L) meta.execSQL("UPDATE meta SET pending = ? WHERE k = ?", arrayOf<Any>(at, key))
                        }
                    }
                }
            }
            for ((key, v) in known) {
                if (key !in seen && v.first != DELETED) {
                    val i = key.indexOf(':')
                    outs.add(Out(key, key.substring(0, i), key.substring(i + 1), null, DELETED, now))
                }
            }

            // 2. Send them (newer change wins on the server).
            for (chunk in outs.chunked(200)) {
                val rows = JSONArray()
                for (o in chunk) rows.put(JSONObject().put("tbl", o.tbl).put("id", o.id).put("data", o.data ?: JSONObject()).put("updated_at", o.at).put("deleted", o.data == null))
                val body = JSONObject().put("p_shop", shop).put("p_device", Prefs.deviceId(context)).put("p_rows", rows).toString().toByteArray()
                val (code, resp) = CloudBackup.call("POST", "${cfg.url}/rest/v1/rpc/push_rows", headers, body)
                if (code !in 200..299) error("Sending failed (code $code): ${String(resp).take(120)}")
                for (o in chunk) meta.execSQL("INSERT OR REPLACE INTO meta (k, hash, pending) VALUES (?, ?, 0)", arrayOf<Any>(o.key, o.hash))
            }

            // 3. Fetch what others changed.
            var last = Prefs.lastSeq(context)
            var applied = 0
            while (true) {
                val url = "${cfg.url}/rest/v1/sync_rows?shop_id=eq.$shop&seq=gt.$last&order=seq.asc&limit=500&select=tbl,id,data,deleted,seq"
                val (code, resp) = CloudBackup.call("GET", url, headers)
                if (code != 200) error("Fetching failed (code $code)")
                val arr = JSONArray(String(resp))
                if (arr.length() == 0) break
                db.beginTransaction()
                try {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val t = r.getString("tbl")
                        val id = r.getString("id")
                        last = maxOf(last, r.getLong("seq"))
                        if (t !in TABLES) continue
                        val key = "$t:$id"
                        if (r.getBoolean("deleted")) {
                            db.execSQL("DELETE FROM $t WHERE id = ?", arrayOf<Any>(id))
                            meta.execSQL("INSERT OR REPLACE INTO meta (k, hash, pending) VALUES (?, ?, 0)", arrayOf<Any>(key, DELETED))
                        } else {
                            val data = r.getJSONObject("data")
                            writeRow(db, t, data)
                            meta.execSQL("INSERT OR REPLACE INTO meta (k, hash, pending) VALUES (?, ?, 0)", arrayOf<Any>(key, sha(canonical(data))))
                        }
                        applied++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                Prefs.setLastSeq(context, last)
                if (arr.length() < 500) break
            }
            if (applied > 0) {
                StockReconcile.run(repo)
                repo.rawDb().invalidationTracker.refreshVersionsAsync()
            }
            null as String?
        }.getOrElse { it.message ?: "No internet." }
    }
}
