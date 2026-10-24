package com.ralledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.Account
import com.ralledger.ui.settings.displayMoney
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Compact, in-flow balance overview for transaction forms. */
@Composable
fun AccountBalancesSummary(accounts: List<Account>) {
    val visibleAccounts = accounts.filterNot { it.isArchived }
    if (visibleAccounts.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Account balances", style = MaterialTheme.typography.labelLarge)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 112.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                visibleAccounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            account.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(displayMoney(account.currentLedgerBalance, account.currency), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Small calculator action for money fields. The calculated amount is returned in currency precision. */
@Composable
fun MoneyCalculatorAction(
    value: String,
    enabled: Boolean = true,
    onResult: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("") }

    IconButton(
        enabled = enabled,
        onClick = {
            expression = value.takeIf { Money.parse(it) != null }.orEmpty()
            open = true
        },
        modifier = Modifier.semantics { contentDescription = "Open calculator" }
    ) {
        Icon(Icons.Outlined.Calculate, contentDescription = null)
    }

    if (open) {
        val result = remember(expression) { BasicMoneyExpression.evaluate(expression) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Calculator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expression,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Expression") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        result?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "Enter a calculation",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    val keys = listOf(
                        listOf("AC", "(", ")", "⌫"),
                        listOf("7", "8", "9", "÷"),
                        listOf("4", "5", "6", "×"),
                        listOf("1", "2", "3", "−"),
                        listOf("0", ".", "+", "=")
                    )
                    keys.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { key ->
                                if (key == "=") {
                                    Button(
                                        onClick = {
                                            result?.let { expression = it.setScale(2, RoundingMode.HALF_UP).toPlainString() }
                                        },
                                        enabled = result != null,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(key) }
                                } else {
                                    TextButton(
                                        onClick = {
                                            expression = when (key) {
                                                "AC" -> ""
                                                "⌫" -> expression.dropLast(1)
                                                else -> (expression + key).take(80)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(key) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        result?.let {
                            onResult(it.setScale(2, RoundingMode.HALF_UP).toPlainString())
                            open = false
                        }
                    },
                    enabled = result != null
                ) { Text("Use amount") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

/** Evaluates basic arithmetic with normal operator precedence and exact decimal inputs. */
internal object BasicMoneyExpression {
    private val context = MathContext(24, RoundingMode.HALF_UP)

    fun evaluate(source: String): BigDecimal? {
        if (source.isBlank() || source.length > 80) return null
        return runCatching {
            Parser(source.replace('×', '*').replace('÷', '/').replace('−', '-')).parse()
        }.getOrNull()
    }

    private class Parser(private val source: String) {
        private var position = 0

        fun parse(): BigDecimal {
            val value = expression()
            whitespace()
            require(position == source.length)
            return value
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                whitespace()
                value = when (peek()) {
                    '+' -> { position++; value.add(term(), context) }
                    '-' -> { position++; value.subtract(term(), context) }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                whitespace()
                value = when (peek()) {
                    '*' -> { position++; value.multiply(factor(), context) }
                    '/' -> {
                        position++
                        value.divide(factor(), context)
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            whitespace()
            return when (peek()) {
                '+' -> { position++; factor() }
                '-' -> { position++; factor().negate(context) }
                '(' -> {
                    position++
                    val value = expression()
                    whitespace()
                    require(peek() == ')')
                    position++
                    value
                }
                else -> number()
            }
        }

        private fun number(): BigDecimal {
            whitespace()
            val start = position
            var hasDigit = false
            var hasDecimal = false
            while (position < source.length) {
                when (source[position]) {
                    in '0'..'9' -> { hasDigit = true; position++ }
                    '.' -> {
                        require(!hasDecimal)
                        hasDecimal = true
                        position++
                    }
                    else -> break
                }
            }
            require(hasDigit)
            return BigDecimal(source.substring(start, position))
        }

        private fun whitespace() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        private fun peek(): Char? = source.getOrNull(position)
    }
}
