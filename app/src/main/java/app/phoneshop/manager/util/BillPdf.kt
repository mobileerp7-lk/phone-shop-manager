package app.phoneshop.manager.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import app.phoneshop.manager.data.Sale
import app.phoneshop.manager.data.SaleLine
import app.phoneshop.manager.data.ShopSettings
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything a printed or shared bill shows. */
data class BillView(
    val settings: ShopSettings,
    val sale: Sale,
    val lines: List<SaleLine>,
    val customerName: String,
    /** account name to amount paid */
    val payments: List<Pair<String, Long>>,
)

object BillPdf {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

    fun dateText(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)

    fun warrantyUntil(createdAt: Long, months: Int): String =
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate().plusMonths(months.toLong())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))

    /** Draws the bill as a narrow receipt-style PDF in the app's cache folder. */
    fun create(context: Context, bill: BillView, currency: CurrencyInfo): File {
        val s = bill.sale
        val width = 300
        val lineHeight = 15f
        val extraRows = bill.lines.size * 2 + bill.payments.size + 14
        val height = (80 + extraRows * lineHeight + 60).toInt()
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
        val c = page.canvas
        val normal = Paint().apply { textSize = 10f; isAntiAlias = true }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 14f }
        val small = Paint(normal).apply { textSize = 8.5f; color = 0xFF555555.toInt() }
        var y = 26f

        fun center(text: String, p: Paint) {
            c.drawText(text, (width - p.measureText(text)) / 2f, y, p); y += lineHeight
        }
        fun row(left: String, right: String, p: Paint = normal) {
            c.drawText(left, 14f, y, p)
            c.drawText(right, width - 14f - p.measureText(right), y, p)
            y += lineHeight
        }
        fun rule() {
            c.drawLine(14f, y - 9f, width - 14f, y - 9f, small); y += 4f
        }

        center(bill.settings.shopName.ifBlank { "Shop" }, title)
        val contact = listOf(bill.settings.address, bill.settings.phone).filter { it.isNotBlank() }.joinToString(" · ")
        if (contact.isNotBlank()) center(contact, small)
        if (bill.settings.taxEnabled && bill.settings.taxNumber.isNotBlank()) center("Tax no. ${bill.settings.taxNumber}", small)
        y += 4f
        row("Bill #${s.billNo}" + if (s.status == "CANCELLED") "  (CANCELLED)" else "", dateText(s.createdAt), bold)
        row("Customer: ${bill.customerName}", "Staff: ${s.staffName}", small)
        rule()
        for (l in bill.lines) {
            row("${l.name} ×${l.qty}", Money.format(l.lineTotal, currency))
            val extra = buildList {
                if (l.imei.isNotBlank()) add("IMEI ${l.imei}")
                if (l.warrantyMonths > 0) add("Warranty till ${warrantyUntil(s.createdAt, l.warrantyMonths)}")
            }.joinToString(" · ")
            if (extra.isNotBlank()) { c.drawText(extra, 14f, y, small); y += lineHeight - 3f }
        }
        rule()
        if (s.discount > 0) row("Discount", "−" + Money.format(s.discount, currency))
        if (s.taxAmount > 0) row("Includes tax", Money.format(s.taxAmount, currency), small)
        row("TOTAL", Money.format(s.total, currency), bold)
        for ((name, amount) in bill.payments) row("Paid: $name", Money.format(amount, currency))
        if (s.due > 0) row("Balance due", Money.format(s.due, currency), bold)
        y += 8f
        center("Thank you!", normal)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "bill-${s.billNo}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Opens the phone's share sheet: WhatsApp, print, save to Drive and so on. */
    fun share(context: Context, file: File, billNo: Int) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bill #$billNo")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share bill #$billNo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
