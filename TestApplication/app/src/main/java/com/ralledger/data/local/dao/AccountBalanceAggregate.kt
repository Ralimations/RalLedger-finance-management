package com.ralledger.data.local.dao

data class AccountBalanceAggregate(
    val accountId: String,
    val lineType: String,
    val totalCentavos: Long
)
