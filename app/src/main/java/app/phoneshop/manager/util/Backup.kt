package app.phoneshop.manager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.documentfile.provider.DocumentFile
import app.phoneshop.manager.data.Repository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore. A backup is one zip with the shop database and the product photos.
 * It can be saved anywhere the phone's file picker reaches, including a folder in Google Drive.
 */
object Backup {
    private const val DB_NAME = "phoneshop.db"
    private const val PREFIX = "phoneshop-backup-"

    fun fileName(): String = PREFIX + LocalDate.now() + ".zip"

    fun write(context: Context, repo: Repository, out: OutputStream) {
        repo.checkpoint()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DB_NAME))
            context.getDatabasePath(DB_NAME).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            File(context.filesDir, "photos").listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("photos/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Writes today's backup into the folder the owner picked (for example in Google Drive) and keeps the last 7. */
    fun writeToFolder(context: Context, repo: Repository, treeUri: String): Boolean = runCatching {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        if (!dir.canWrite()) return false
        dir.findFile(fileName())?.delete()
        val file = dir.createFile("application/zip", fileName()) ?: return false
        context.contentResolver.openOutputStream(file.uri)?.use { write(context, repo, it) } ?: return false
        dir.listFiles().filter { it.name?.startsWith(PREFIX) == true }
            .sortedByDescending { it.name }.drop(7).forEach { it.delete() }
        true
    }.getOrDefault(false)

    /**
     * Replaces the shop's data with a backup. Returns an error message, or null when done
     * (the caller then restarts the app).
     */
    fun restore(context: Context, repo: Repository, input: InputStream): String? {
        if (Prefs.shopId(context).isNotBlank()) return "Live sharing is on. Turn it off first, otherwise the shop's online data would bring the old records back."
        val temp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var hasDb = false
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory && (name == DB_NAME || name.startsWith("photos/")) && !name.contains("..")) {
                        val target = File(temp, name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        if (name == DB_NAME) hasDb = true
                    }
                    e = zip.nextEntry
                }
            }
            if (!hasDb) return "That file is not a backup from this app."
            val backupVersion = android.database.sqlite.SQLiteDatabase.openDatabase(
                File(temp, DB_NAME).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { it.version }
            if (backupVersion != repo.schemaVersion()) {
                return "This backup was made by a different version of the app, so it cannot be restored here."
            }
            repo.close()
            val dbFile = context.getDatabasePath(DB_NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(temp, DB_NAME).copyTo(dbFile, overwrite = true)
            val photos = File(context.filesDir, "photos")
            File(temp, "photos").listFiles()?.let { list ->
                photos.mkdirs()
                list.forEach { it.copyTo(File(photos, it.name), overwrite = true) }
            }
            return null
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Starts the app again from scratch so it opens the restored data. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            context.startActivity(Intent.makeRestartActivityTask(intent.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Process.killProcess(Process.myPid())
    }
}
