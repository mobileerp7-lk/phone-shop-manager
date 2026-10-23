package app.phoneshop.manager.util

import android.content.Context

/** Small phone-only settings that do not belong in the shop database (so they never travel in a backup). */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("device", Context.MODE_PRIVATE)

    fun billFooter(c: Context): String = p(c).getString("billFooter", "Thank you!") ?: "Thank you!"
    fun setBillFooter(c: Context, v: String) = p(c).edit().putString("billFooter", v).apply()

    /** 58 or 80 (millimetres). */
    fun paperMm(c: Context): Int = p(c).getInt("paperMm", 58)
    fun setPaperMm(c: Context, v: Int) = p(c).edit().putInt("paperMm", v).apply()

    fun printerAddress(c: Context): String = p(c).getString("printerAddress", "") ?: ""
    fun printerName(c: Context): String = p(c).getString("printerName", "") ?: ""
    fun setPrinter(c: Context, address: String, name: String) =
        p(c).edit().putString("printerAddress", address).putString("printerName", name).apply()

    /** The staff member who turned on fingerprint unlock on this phone, or blank. */
    fun fingerprintStaff(c: Context): String = p(c).getString("fingerprintStaff", "") ?: ""
    fun setFingerprintStaff(c: Context, id: String) = p(c).edit().putString("fingerprintStaff", id).apply()

    /** OFF, MAIN (this phone sends its data online) or VIEWER (this phone only shows the main phone's data). */
    fun syncMode(c: Context): String = p(c).getString("syncMode", "OFF") ?: "OFF"
    fun setSyncMode(c: Context, v: String) = p(c).edit().putString("syncMode", v).apply()
    fun syncEtag(c: Context): String = p(c).getString("syncEtag", "") ?: ""
    fun setSyncEtag(c: Context, v: String) = p(c).edit().putString("syncEtag", v).apply()
    fun syncMark(c: Context): Long = p(c).getLong("syncMark", 0L)
    fun setSyncMark(c: Context, v: Long) = p(c).edit().putLong("syncMark", v).apply()

    fun language(c: Context): String = p(c).getString("language", "en") ?: "en"
    fun setLanguage(c: Context, v: String) = p(c).edit().putString("language", v).apply()
}
