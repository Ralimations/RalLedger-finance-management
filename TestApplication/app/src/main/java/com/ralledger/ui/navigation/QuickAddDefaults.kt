package com.ralledger.ui.navigation

internal enum class QuickAddAccountDefaultSource {
    CONTEXTUAL,
    PREFERRED,
    RECENT,
    FIRST_AVAILABLE,
    NONE
}

internal data class QuickAddAccountDefault(
    val accountId: String,
    val source: QuickAddAccountDefaultSource
)

internal enum class QuickAddCategoryDefaultSource {
    RECENT,
    FIRST_AVAILABLE,
    NONE
}

internal data class QuickAddCategoryDefault(
    val categoryId: String,
    val source: QuickAddCategoryDefaultSource
)

internal fun resolveQuickAddCategoryDefault(
    lastUsedCategoryId: String?,
    eligibleCategoryIds: List<String>
): QuickAddCategoryDefault = when {
    lastUsedCategoryId in eligibleCategoryIds -> QuickAddCategoryDefault(
        lastUsedCategoryId.orEmpty(),
        QuickAddCategoryDefaultSource.RECENT
    )
    eligibleCategoryIds.isNotEmpty() -> QuickAddCategoryDefault(
        eligibleCategoryIds.first(),
        QuickAddCategoryDefaultSource.FIRST_AVAILABLE
    )
    else -> QuickAddCategoryDefault("", QuickAddCategoryDefaultSource.NONE)
}

internal fun resolveQuickAddAccountDefault(
    contextualAccountId: String?,
    preferredAccountId: String?,
    lastUsedAccountId: String?,
    eligibleAccountIds: List<String>
): QuickAddAccountDefault = when {
    contextualAccountId in eligibleAccountIds -> QuickAddAccountDefault(
        contextualAccountId.orEmpty(),
        QuickAddAccountDefaultSource.CONTEXTUAL
    )
    preferredAccountId in eligibleAccountIds -> QuickAddAccountDefault(
        preferredAccountId.orEmpty(),
        QuickAddAccountDefaultSource.PREFERRED
    )
    lastUsedAccountId in eligibleAccountIds -> QuickAddAccountDefault(
        lastUsedAccountId.orEmpty(),
        QuickAddAccountDefaultSource.RECENT
    )
    eligibleAccountIds.isNotEmpty() -> QuickAddAccountDefault(
        eligibleAccountIds.first(),
        QuickAddAccountDefaultSource.FIRST_AVAILABLE
    )
    else -> QuickAddAccountDefault("", QuickAddAccountDefaultSource.NONE)
}

internal fun resolveQuickAddAccountId(
    contextualAccountId: String?,
    preferredAccountId: String?,
    lastUsedAccountId: String?,
    eligibleAccountIds: List<String>
): String = resolveQuickAddAccountDefault(
    contextualAccountId = contextualAccountId,
    preferredAccountId = preferredAccountId,
    lastUsedAccountId = lastUsedAccountId,
    eligibleAccountIds = eligibleAccountIds
).accountId
