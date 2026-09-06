package dev.tagalong.engine

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * The physical representation of an embedded location in an MP4 file.
 *
 * FFprobe normalizes both of these forms to a logical `location` tag, but gallery
 * applications do not necessarily do so. Keep this separate from the normalized
 * format tag maps in [MediaProbe].
 */
enum class LocationRepresentation(val label: String) {
    QUICKTIME_XYZ("QuickTime ©xyz"),
    GENERIC_MDTA("generic mdta"),
    BOTH("QuickTime ©xyz + generic mdta"),
    ABSENT("absent"),
}

/** Raw location metadata found by [Mp4LocationMetadata]. */
data class LocationRepresentationInfo(
    val quickTimeLocations: List<QuickTimeLocation> = emptyList(),
    val genericMdtaKeys: Set<String> = emptySet(),
) {
    val hasQuickTime: Boolean get() = quickTimeLocations.isNotEmpty()
    val hasGenericMdta: Boolean get() = genericMdtaKeys.isNotEmpty()

    val representation: LocationRepresentation
        get() = when {
            hasQuickTime && hasGenericMdta -> LocationRepresentation.BOTH
            hasQuickTime -> LocationRepresentation.QUICKTIME_XYZ
            hasGenericMdta -> LocationRepresentation.GENERIC_MDTA
            else -> LocationRepresentation.ABSENT
        }

    /** The first raw QuickTime payload, useful for the canonical one-location samples. */
    val quickTimePayload: ByteArray?
        get() = quickTimeLocations.firstOrNull()?.payload

    fun quickTimePayloadsEqual(other: LocationRepresentationInfo): Boolean =
        quickTimeLocations.size == other.quickTimeLocations.size &&
            quickTimeLocations.zip(other.quickTimeLocations).all { (left, right) ->
                left.payload.contentEquals(right.payload)
            }
}

/**
 * A raw QuickTime location box. [boxBytes] includes the MP4 header; [payload] is the
 * exact bytes after that header. Neither value is parsed or coordinate-normalized.
 */
data class QuickTimeLocation(
    val boxBytes: ByteArray,
    val payload: ByteArray,
)

/**
 * Small, dependency-free MP4 box walker for preservation-critical metadata.
 *
 * This is intentionally not a general media parser. It understands MP4 box headers,
 * the container boxes needed to reach `moov/udta`, and the `meta`/`keys` structure
 * emitted for generic `mdta` metadata. Unknown boxes are retained as opaque bytes.
 */
object Mp4LocationMetadata {

    fun inspect(file: File): LocationRepresentationInfo = inspect(file.readBytes())

    fun inspect(bytes: ByteArray): LocationRepresentationInfo {
        require(bytes.isNotEmpty()) { "Cannot inspect an empty MP4 file" }
        val root = parseBoxes(bytes, 0, bytes.size)
        val quickTime = mutableListOf<QuickTimeLocation>()
        val genericKeys = linkedSetOf<String>()

        fun visit(box: Mp4Box, path: List<String>) {
            val currentPath = path + box.type
            if (currentPath.takeLast(3) == listOf("moov", "udta", "©xyz")) {
                quickTime += QuickTimeLocation(
                    boxBytes = bytes.copyOfRange(box.start, box.end),
                    payload = bytes.copyOfRange(box.payloadStart, box.end),
                )
            }
            if (box.type == "meta") {
                genericKeys += readLocationKeys(bytes, box)
            }
            childrenOf(box, bytes).forEach { child -> visit(child, currentPath) }
        }

        root.forEach { visit(it, emptyList()) }
        return LocationRepresentationInfo(quickTime, genericKeys)
    }

    private fun readLocationKeys(bytes: ByteArray, meta: Mp4Box): Set<String> {
        // A meta box is a FullBox: version/flags precede its child boxes.
        if (meta.payloadStart + 4 > meta.end) return emptySet()
        val children = childrenOf(meta, bytes)
        val keys = children.firstOrNull { it.type == "keys" } ?: return emptySet()
        if (keys.payloadStart + 8 > keys.end) return emptySet()

        val entryCount = readUInt32(bytes, keys.payloadStart + 4)
        if (entryCount > Int.MAX_VALUE) {
            throw IllegalArgumentException("MP4 keys entry count is too large: $entryCount")
        }
        var cursor = keys.payloadStart + 8
        val found = linkedSetOf<String>()
        repeat(entryCount.toInt()) {
            if (cursor + 8 > keys.end) {
                throw IllegalArgumentException("Truncated MP4 mdta key entry")
            }
            val entrySize = readUInt32(bytes, cursor).toIntChecked("mdta key size")
            if (entrySize < 8 || cursor + entrySize > keys.end) {
                throw IllegalArgumentException("Invalid MP4 mdta key size: $entrySize")
            }
            val namespace = decodeType(bytes, cursor + 4)
            val key = String(bytes, cursor + 8, entrySize - 8, StandardCharsets.UTF_8)
            if (namespace == "mdta" && (key == "location" || key == "location-eng")) {
                found += key
            }
            cursor += entrySize
        }
        return found
    }

    internal fun childrenOf(box: Mp4Box, bytes: ByteArray): List<Mp4Box> {
        if (box.type !in CONTAINER_TYPES) return emptyList()
        val childStart = if (box.type == "meta") metaChildStart(box, bytes) else box.payloadStart
        if (childStart > box.end) {
            throw IllegalArgumentException("MP4 ${box.type} box has no room for children")
        }
        return parseBoxes(bytes, childStart, box.end)
    }

    private fun metaChildStart(box: Mp4Box, bytes: ByteArray): Int {
        // Both forms occur in real files: QuickTime metadata commonly puts `hdlr` at
        // the payload start, while ISO FullBox metadata puts version/flags there first.
        return if (looksLikeBoxHeader(bytes, box.payloadStart, box.end)) {
            box.payloadStart
        } else {
            box.payloadStart + 4
        }
    }

    private fun looksLikeBoxHeader(bytes: ByteArray, start: Int, end: Int): Boolean {
        if (start < 0 || start + 8 > end) return false
        val size = readUInt32(bytes, start)
        val type = decodeType(bytes, start + 4)
        if (!type.all { it.code in 0x20..0x7e || it == '©' }) return false
        return when (size) {
            0L -> true
            1L -> start + 16 <= end && readUInt64(bytes, start + 8) <= (end - start).toLong()
            else -> size >= 8L && size <= (end - start).toLong()
        }
    }

    internal fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var cursor = start
        while (cursor < end) {
            if (end - cursor < 8) {
                throw IllegalArgumentException("Truncated MP4 box header at offset $cursor")
            }
            val size32 = readUInt32(bytes, cursor)
            val type = decodeType(bytes, cursor + 4)
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    // A zero-sized box extends to its containing box's end.
                    headerSize = 8
                    boxSize = (end - cursor).toLong()
                }
                1L -> {
                    if (end - cursor < 16) {
                        throw IllegalArgumentException("Truncated extended MP4 box header at offset $cursor")
                    }
                    headerSize = 16
                    boxSize = readUInt64(bytes, cursor + 8)
                }
                else -> {
                    headerSize = 8
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || boxSize > (end - cursor).toLong()) {
                throw IllegalArgumentException("Invalid MP4 box size $boxSize for $type at $cursor")
            }
            val boxEnd = cursor + boxSize.toIntChecked("box end")
            boxes += Mp4Box(type, cursor, headerSize, cursor + headerSize, boxEnd)
            cursor = boxEnd
        }
        return boxes
    }

    internal fun decodeType(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, StandardCharsets.ISO_8859_1)

    internal fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    internal fun readUInt64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { index ->
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xff)
        }
        require(value >= 0L) { "MP4 box size exceeds supported JVM array range" }
        return value
    }

    private fun Long.toIntChecked(description: String): Int {
        require(this in 0..Int.MAX_VALUE) { "MP4 $description is out of range: $this" }
        return toInt()
    }

    internal data class Mp4Box(
        val type: String,
        val start: Int,
        val headerSize: Int,
        val payloadStart: Int,
        val end: Int,
    )

    internal val CONTAINER_TYPES = setOf(
        "moov", "udta", "meta", "ilst", "trak", "mdia", "minf", "stbl", "dinf",
        "edts", "mvex", "moof", "traf", "mfra", "skip", "ipro", "sinf", "schi", "wave",
    )
}
