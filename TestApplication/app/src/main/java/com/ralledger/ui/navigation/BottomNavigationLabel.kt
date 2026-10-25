package com.ralledger.ui.navigation

internal fun displayNavigationLabel(
    title: String,
    compactTitle: String,
    screenWidthDp: Int,
    fontScale: Float
): String = if (screenWidthDp <= 360 && fontScale >= 1.2f) compactTitle else title
