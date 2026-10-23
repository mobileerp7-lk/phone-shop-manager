package app.phoneshop.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.phoneshop.manager.data.Staff

/** Who is using the app right now, and what that role may see or do. */
object Session {
    var staff by mutableStateOf<Staff?>(null)

    val role: String get() = staff?.role ?: "OWNER"
    val isOwner: Boolean get() = role == "OWNER"
    /** Buying prices, profit and stock value are hidden from cashiers. */
    val canSeeCost: Boolean get() = role != "CASHIER"
    val canSeeMoney: Boolean get() = role != "CASHIER"
    val canCancel: Boolean get() = role != "CASHIER"
    val canManageShop: Boolean get() = role == "OWNER"
}

fun roleLabel(role: String) = when (role) {
    "OWNER" -> "Owner"
    "MANAGER" -> "Manager"
    else -> "Cashier"
}
