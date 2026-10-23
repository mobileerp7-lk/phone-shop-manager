package app.phoneshop.manager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.phoneshop.manager.data.CartLine

/** The sale being built. Kept for the whole app session so it survives switching tabs. */
class CartState {
    val lines = mutableStateListOf<CartLine>()
    var discount by mutableStateOf("")
    var customerId by mutableStateOf<String?>(null)

    /** Set to a tab number to make the main screen jump there (1 = Sell). */
    var requestTab by mutableStateOf<Int?>(null)

    val subtotal: Long get() = lines.sumOf { it.unitPrice * it.qty }

    fun clear() {
        lines.clear()
        discount = ""
        customerId = null
    }
}
