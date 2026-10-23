package app.phoneshop.manager.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String, val symbol: String)

data class CountryInfo(val code: String, val name: String, val currency: String)

object Locales {
    val currencies = listOf(
        CurrencyInfo("LKR", "Sri Lankan Rupee", "Rs."),
        CurrencyInfo("INR", "Indian Rupee", "₹"),
        CurrencyInfo("USD", "US Dollar", "$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("GBP", "British Pound", "£"),
        CurrencyInfo("AED", "UAE Dirham", "AED"),
        CurrencyInfo("PKR", "Pakistani Rupee", "Rs."),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳"),
        CurrencyInfo("NPR", "Nepalese Rupee", "Rs."),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "MVR"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$"),
        CurrencyInfo("AUD", "Australian Dollar", "A$"),
        CurrencyInfo("CAD", "Canadian Dollar", "C$"),
        CurrencyInfo("SAR", "Saudi Riyal", "SAR"),
        CurrencyInfo("QAR", "Qatari Riyal", "QAR"),
    )

    val countries = listOf(
        CountryInfo("LK", "Sri Lanka", "LKR"),
        CountryInfo("IN", "India", "INR"),
        CountryInfo("PK", "Pakistan", "PKR"),
        CountryInfo("BD", "Bangladesh", "BDT"),
        CountryInfo("NP", "Nepal", "NPR"),
        CountryInfo("MV", "Maldives", "MVR"),
        CountryInfo("AE", "United Arab Emirates", "AED"),
        CountryInfo("SA", "Saudi Arabia", "SAR"),
        CountryInfo("QA", "Qatar", "QAR"),
        CountryInfo("MY", "Malaysia", "MYR"),
        CountryInfo("SG", "Singapore", "SGD"),
        CountryInfo("GB", "United Kingdom", "GBP"),
        CountryInfo("US", "United States", "USD"),
        CountryInfo("AU", "Australia", "AUD"),
        CountryInfo("CA", "Canada", "CAD"),
    )

    fun currency(code: String): CurrencyInfo =
        currencies.firstOrNull { it.code == code } ?: currencies.first()

    fun country(code: String): CountryInfo =
        countries.firstOrNull { it.code == code } ?: countries.first()
}

/** Money is stored as whole minor units (two decimals) and formatted only for display. */
object Money {
    fun format(minor: Long, currency: CurrencyInfo): String {
        val negative = minor < 0
        val abs = kotlin.math.abs(minor)
        val whole = String.format(Locale.US, "%,d", abs / 100)
        val cents = (abs % 100).toString().padStart(2, '0')
        val text = "${currency.symbol} $whole.$cents"
        return if (negative) "-$text" else text
    }

    fun parse(text: String): Long? {
        val clean = text.trim().replace(",", "")
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun toInput(minor: Long): String =
        if (minor == 0L) "" else BigDecimal(minor).movePointLeft(2).setScale(2).toPlainString()
}
