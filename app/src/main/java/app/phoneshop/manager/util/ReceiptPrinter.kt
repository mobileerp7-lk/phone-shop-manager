package app.phoneshop.manager.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Plain-text receipts for Bluetooth thermal printers that speak ESC/POS (most 58 mm and 80 mm ones).
 * The printer is paired once in the phone's Bluetooth settings, then chosen here.
 */
object ReceiptPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun pairedPrinters(context: Context): List<BluetoothDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.toList().sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
    }

    fun columns(paperMm: Int) = if (paperMm >= 80) 48 else 32

    /** Returns null when printed, or a short message for the owner. */
    @SuppressLint("MissingPermission")
    fun print(context: Context, text: String): String? {
        val address = Prefs.printerAddress(context)
        if (address.isBlank()) return "Choose a receipt printer first (Settings > Receipt printer)."
        val adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return "This phone has no Bluetooth."
        if (!adapter.isEnabled) return "Turn Bluetooth on first."
        return runCatching {
            val device = adapter.getRemoteDevice(address)
            val socket = device.createRfcommSocketToServiceRecord(SPP)
            adapter.cancelDiscovery()
            socket.connect()
            socket.use {
                val out = ByteArrayOutputStream()
                out.write(byteArrayOf(0x1B, 0x40)) // reset
                out.write(text.toByteArray(Charsets.ISO_8859_1))
                out.write("\n\n\n\n".toByteArray())
                out.write(byteArrayOf(0x1D, 0x56, 0x01)) // cut, ignored by printers without a cutter
                it.outputStream.write(out.toByteArray())
                it.outputStream.flush()
                Thread.sleep(800)
            }
            null as String?
        }.getOrElse { "Could not reach the printer. Is it on and paired?" }
    }
}
