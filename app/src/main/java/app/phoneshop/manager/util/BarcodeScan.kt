package app.phoneshop.manager.util

import android.content.Context
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Scans a barcode with Google's built-in scanner screen. It needs no camera permission from this app. */
object BarcodeScan {
    fun scan(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(onResult) }
            .addOnFailureListener { onError("The scanner could not open. Update Google Play services, or type the code.") }
    }
}
