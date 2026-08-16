package dev.tagalong.app

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** `mm:ss.s` label for a millisecond position (design D3). */
fun formatMmSsTenths(ms: Long): String {
    val totalTenths = ms / 100
    val minutes = totalTenths / 600
    val seconds = (totalTenths / 10) % 60
    val tenths = totalTenths % 10
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

/** Human-readable gallery date shown as proof the original capture date was applied (spec). */
fun formatGalleryDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))
