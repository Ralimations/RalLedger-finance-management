package com.ralledger.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.ralledger.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId

enum class DateDisplayFormat(val label: String, internal val pattern: String) {
    MONTH_DAY_YEAR("Oct 23, 2026", "MMM d, yyyy"),
    DAY_MONTH_YEAR("23 Oct 2026", "d MMM yyyy"),
    YEAR_MONTH_DAY("2026-10-23", "yyyy-MM-dd")
}

enum class TransactionListDensity(val label: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact")
}

enum class CurrencyDisplayFormat(val label: String) {
    SYMBOL("₱ Symbol"),
    CODE("PHP Code")
}

val LocalDateDisplayFormat = compositionLocalOf { DateDisplayFormat.MONTH_DAY_YEAR }
val LocalCurrencyDisplayFormat = compositionLocalOf { CurrencyDisplayFormat.SYMBOL }

@Composable
fun ProvideDateDisplayFormat(
    format: DateDisplayFormat,
    currencyFormat: CurrencyDisplayFormat,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDateDisplayFormat provides format,
        LocalCurrencyDisplayFormat provides currencyFormat,
        content = content
    )
}

@Composable
fun displayDate(timestamp: Long, includeTime: Boolean = false): String {
    val format = LocalDateDisplayFormat.current
    return remember(timestamp, format, includeTime) {
        formatLedgerDate(timestamp, format, includeTime)
    }
}

@Composable
fun displayDate(date: LocalDate): String = displayDate(
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
)

@Composable
fun displayMoney(value: Money, currencyCode: String = "PHP"): String {
    val format = LocalCurrencyDisplayFormat.current
    return remember(value, currencyCode, format) { formatLedgerMoney(value, currencyCode, format) }
}

internal fun formatLedgerDate(
    timestamp: Long,
    format: DateDisplayFormat,
    includeTime: Boolean = false
): String {
    val pattern = format.pattern + if (includeTime) " HH:mm" else ""
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

internal fun formatLedgerMoney(value: Money, currencyCode: String, format: CurrencyDisplayFormat): String =
    when (format) {
        CurrencyDisplayFormat.SYMBOL -> value.format(currencyCode)
        CurrencyDisplayFormat.CODE -> {
            val numberFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            "$currencyCode ${numberFormat.format(BigDecimal.valueOf(value.centavos, 2))}"
        }
    }
