package com.ralledger.ui.navigation

internal enum class QuickAddDismissAction {
    DISMISS,
    CONFIRM_DISCARD,
    IGNORE
}

internal fun quickAddDismissAction(hasUserEdited: Boolean, isSaving: Boolean): QuickAddDismissAction = when {
    isSaving -> QuickAddDismissAction.IGNORE
    hasUserEdited -> QuickAddDismissAction.CONFIRM_DISCARD
    else -> QuickAddDismissAction.DISMISS
}
