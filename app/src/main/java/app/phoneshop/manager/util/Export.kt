package app.phoneshop.manager.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.phoneshop.manager.data.Repository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes the shop's records as CSV files (Excel and Google Sheets open them) in one zip. */
object Export {
    private fun q(v: Any?): String {
        val t = v?.toString() ?: ""
        return if (t.any { it == ',' || it == '"' || it == '\n' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }
    private fun line(vararg v: Any?) = v.joinToString(",") { q(it) } + "\n"
    private fun date(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    private fun m(x: Long) = Money.toInput(x)

    suspend fun create(context: Context, repo: Repository, canSeeCost: Boolean): File {
        val products = repo.products.first()
        val contacts = repo.contacts.first()
        val sales = repo.sales.first()
        val lines = repo.allSaleLines.first()
        val entries = repo.entries.first()
        val accounts = repo.accounts.first().associateBy { it.id }
        val saleById = sales.associateBy { it.id }
        val contactById = contacts.associateBy { it.id }

        val files = linkedMapOf<String, String>()
        files["products.csv"] = line("Item code", "Name", "Barcode", "Selling price", "Low stock level") +
            products.joinToString("") { line(it.itemCode, it.name, it.barcode, m(it.sellPrice), it.lowStockLevel) }
        files["customers-suppliers.csv"] = line("Type", "Name", "Phone", "Address", "Opening balance") +
            contacts.joinToString("") { line(it.kind, it.name, it.phone, it.address, m(it.openingBalance)) }
        files["bills.csv"] = line("Bill no", "Date", "Customer", "Total", "Paid", "Due", "Status", "Staff") +
            sales.joinToString("") { line(it.billNo, date(it.createdAt), contactById[it.customerId]?.name ?: "Walk-in", m(it.total), m(it.paid), m(it.due), it.status, it.staffName) }
        files["bill-lines.csv"] = line("Bill no", "Item", "Qty", "Unit price", "Line total") +
            lines.joinToString("") { line(saleById[it.saleId]?.billNo, it.name, it.qty, m(it.unitPrice), m(it.lineTotal)) }
        if (canSeeCost) {
            files["money.csv"] = line("Date", "Account", "Kind", "Amount (minus = out)", "Note", "Voided") +
                entries.joinToString("") { line(date(it.entryDate), accounts[it.accountId]?.name, it.kind, m(it.amount), it.note, if (it.voidedAt != null) "yes" else "") }
        }

        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        val zip = File(dir, "shop-data-${LocalDate.now()}.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write("﻿".toByteArray() + text.toByteArray())
                z.closeEntry()
            }
        }
        return zip
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Save or send the shop data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
