package com.ralledger.ui.widgets

internal const val EXTRA_WIDGET_TRANSACTION_TYPE = "com.ralledger.extra.WIDGET_TRANSACTION_TYPE"

internal fun normalizeQuickAddTransactionType(value: String?): String? =
    value?.uppercase()?.takeIf { it in setOf("INCOME", "EXPENSE", "TRANSFER", "REFUND") }

internal fun normalizeWidgetTransactionType(value: String?): String? =
    value?.uppercase()?.takeIf { it in setOf("INCOME", "EXPENSE", "TRANSFER") }
