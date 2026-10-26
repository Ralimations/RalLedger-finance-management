package com.ralledger.ui.components

import java.util.Calendar
import java.util.TimeZone

internal fun localTimestampToPickerDateMillis(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val localDate = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localDate.get(Calendar.YEAR),
            localDate.get(Calendar.MONTH),
            localDate.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0
        )
    }.timeInMillis
}

internal fun pickerDateToLocalTimestamp(
    pickerMillis: Long,
    preserveTimeFrom: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = preserveTimeFrom }
    val hour = local.get(Calendar.HOUR_OF_DAY)
    val minute = local.get(Calendar.MINUTE)
    val second = local.get(Calendar.SECOND)
    val millisecond = local.get(Calendar.MILLISECOND)
    local.set(
        utcDate.get(Calendar.YEAR),
        utcDate.get(Calendar.MONTH),
        utcDate.get(Calendar.DAY_OF_MONTH),
        hour,
        minute,
        second
    )
    local.set(Calendar.MILLISECOND, millisecond)
    return local.timeInMillis
}

internal fun localTimestampWithTime(
    timestamp: Long,
    hour: Int,
    minute: Int,
    timeZone: TimeZone = TimeZone.getDefault()
): Long = Calendar.getInstance(timeZone).apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
