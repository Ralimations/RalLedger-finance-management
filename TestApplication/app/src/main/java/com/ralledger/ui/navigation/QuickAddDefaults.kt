package com.ralledger.ui.navigation

internal fun resolveQuickAddAccountId(
    contextualAccountId: String?,
    preferredAccountId: String?,
    lastUsedAccountId: String?,
    eligibleAccountIds: List<String>
): String = contextualAccountId?.takeIf { it in eligibleAccountIds }
    ?: preferredAccountId?.takeIf { it in eligibleAccountIds }
    ?: lastUsedAccountId?.takeIf { it in eligibleAccountIds }
    ?: eligibleAccountIds.firstOrNull().orEmpty()
