package app.phoneshop.manager.util

import android.content.Context
import app.phoneshop.manager.data.Repository
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online backup to the shop's own Supabase project. The address, public key and login are typed
 * in the app and kept only on this phone, never in the code.
 */
object CloudBackup {
    private const val PREFS = "cloud"
    private const val BUCKET = "backups"

    data class Config(val url: String, val key: String, val email: String, val password: String) {
        val ready get() = url.isNotBlank() && key.isNotBlank() && email.isNotBlank() && password.isNotBlank()
    }

    fun load(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(p.getString("url", "") ?: "", p.getString("key", "") ?: "", p.getString("email", "") ?: "", p.getString("password", "") ?: "")
    }

    fun save(context: Context, c: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", c.url.trim().trimEnd('/')).putString("key", c.key.trim())
            .putString("email", c.email.trim()).putString("password", c.password).apply()
    }

    private class Session(val token: String, val userId: String)

    private fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray? = null): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 120000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        return code to bytes
    }

    private fun head(url: String, headers: Map<String, String>): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.connectTimeout = 20000
        c.readTimeout = 30000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val code = c.responseCode
        val tag = (c.getHeaderField("ETag") ?: c.getHeaderField("Last-Modified") ?: "") + (c.getHeaderField("Content-Length") ?: "")
        c.disconnect()
        return code to tag
    }

    /** A value that changes whenever the online copy is replaced; null when there is none or no internet. */
    fun onlineVersion(c: Config): String? = runCatching {
        val s = signIn(c)
        val (code, tag) = head(
            "${c.url}/storage/v1/object/authenticated/$BUCKET/${s.userId}/latest.zip",
            mapOf("apikey" to c.key, "Authorization" to "Bearer ${s.token}"),
        )
        if (code == 200 && tag.isNotBlank()) tag else null
    }.getOrNull()

    /** Marks how much the shop data has changed on this phone (file sizes and times). */
    fun localMark(context: Context, repo: Repository): Long {
        repo.checkpoint()
        val db = context.getDatabasePath("phoneshop.db")
        return db.lastModified() + db.length()
    }

    /** Main phone: sends the data online when something changed since the last send. */
    fun pushIfChanged(context: Context, repo: Repository): Boolean {
        val c = load(context)
        if (!c.ready) return false
        val mark = localMark(context, repo)
        if (mark == Prefs.syncMark(context)) return false
        if (upload(context, repo, c) != null) return false
        Prefs.setSyncMark(context, mark)
        onlineVersion(c)?.let { Prefs.setSyncEtag(context, it) }
        return true
    }

    private fun signIn(c: Config): Session {
        val body = JSONObject().put("email", c.email).put("password", c.password).toString().toByteArray()
        val (code, bytes) = call(
            "POST", "${c.url}/auth/v1/token?grant_type=password",
            mapOf("apikey" to c.key, "Content-Type" to "application/json"), body,
        )
        if (code != 200) error("Could not log in. Check the address, key, email and password.")
        val j = JSONObject(String(bytes))
        return Session(j.getString("access_token"), j.getJSONObject("user").getString("id"))
    }

    private fun objectUrl(c: Config, s: Session) = "${c.url}/storage/v1/object/$BUCKET/${s.userId}/latest.zip"

    /** Returns null on success, or a short message for the owner. */
    fun test(c: Config): String? = runCatching { signIn(c); null as String? }.getOrElse { it.message ?: "Could not connect." }

    fun upload(context: Context, repo: Repository, c: Config): String? = runCatching {
        val s = signIn(c)
        val buf = ByteArrayOutputStream()
        Backup.write(context, repo, buf)
        val (code, _) = call(
            "POST", objectUrl(c, s),
            mapOf("apikey" to c.key, "Authorization" to "Bearer ${s.token}", "Content-Type" to "application/zip", "x-upsert" to "true"),
            buf.toByteArray(),
        )
        if (code in 200..299) null else "The upload was refused (code $code). Did you run the setup script in Supabase?"
    }.getOrElse { it.message ?: "No internet, or the address is wrong." }

    /** Downloads the latest online backup into a temp file, or returns an error message. */
    fun download(context: Context, c: Config): Pair<File?, String?> = runCatching {
        val s = signIn(c)
        val (code, bytes) = call(
            "GET", "${c.url}/storage/v1/object/authenticated/$BUCKET/${s.userId}/latest.zip",
            mapOf("apikey" to c.key, "Authorization" to "Bearer ${s.token}"),
        )
        if (code != 200) return@runCatching null to "No online backup found yet."
        val f = File(context.cacheDir, "cloud-restore.zip")
        f.writeBytes(bytes)
        f to null
    }.getOrElse { null to (it.message ?: "No internet, or the address is wrong.") }

    /** Paste this once into the Supabase "SQL editor". It is the same file as supabase/migrations/0001_shop_sync.sql. */
    fun setupSql(context: Context): String =
        context.assets.open("0001_shop_sync.sql").bufferedReader().use { it.readText() }
}
