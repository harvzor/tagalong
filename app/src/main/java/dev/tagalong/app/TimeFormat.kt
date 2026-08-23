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

/**
 * Parses `M:SS.t` (e.g. "1:13.3") → milliseconds, or null if the input does not match exactly
 * (design D3). Regex: `^\d+:[0-5]\d\.\d$` — partial matches (no tenths, extra tenths digits,
 * out-of-range seconds) are rejected.
 */
fun parseMmSsTenths(input: String): Long? {
    if (!input.matches(Regex("""^\d+:[0-5]\d\.\d$"""))) return null
    val colonIdx = input.indexOf(':')
    val dotIdx = input.indexOf('.')
    val minutes = input.substring(0, colonIdx).toLong()
    val seconds = input.substring(colonIdx + 1, dotIdx).toLong()
    val tenths = input.substring(dotIdx + 1).toLong()
    return (minutes * 60 + seconds) * 1000 + tenths * 100
}

/** Human-readable gallery date shown as proof the original capture date was applied (spec). */
fun formatGalleryDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))
