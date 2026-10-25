package com.ralledger.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Shared monetary input with decimal keyboard and the in-place calculator action. */
@Composable
fun MoneyAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    singleLine: Boolean = true,
    calculatorEnabled: Boolean = enabled
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            MoneyCalculatorAction(value = value, enabled = calculatorEnabled && enabled) { result ->
                onValueChange(result)
            }
        }
    )
}
