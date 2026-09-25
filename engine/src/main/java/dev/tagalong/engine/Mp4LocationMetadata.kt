package dev.tagalong.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Reading a metadata atom larger than this from a file indicates a malformed or
 * adversarial container; location atoms are tens of bytes in every known writer.
 */
private const val MAX_METADATA_ATOM_BYTES = 4 * 1024 * 1024

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
    /**
     * True when the file also carries a 3GPP LocationInformation (`loci`) box under a `moov`.
     * Camera sources never write it — it is the box FFmpeg's mov muxer *invents* when it
     * translates a QuickTime `©xyz` for MP4 output, re-quantizing the coordinates and
     * fabricating an altitude (see `FfmpegCutEngine` / change `cut-command-honesty`).
     * A cut output is expected to have no such box: the engine suppresses ffmpeg's location
     * mistranslation at the dictionary so `mov_write_loci_tag` bails, and the finalizer
     * restores the true `©xyz`.
     */
    val hasThreeGppLocationBox: Boolean = false,
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
 *
 * The walker runs against an [Mp4ByteSource] so the same grammar serves in-memory
 * bytes and whole files. The file-backed [inspect] never buffers the file: box
 * headers are read a few bytes at a time and only metadata atoms are materialized.
 * A phone-size video is megabytes of media per gigabyte, so this keeps memory flat
 * regardless of the cut's size.
 */
object Mp4LocationMetadata {

    fun inspect(file: File): LocationRepresentationInfo =
        Mp4FileByteSource(file).use { inspect(it) }

    fun inspect(bytes: ByteArray): LocationRepresentationInfo {
        require(bytes.isNotEmpty()) { "Cannot inspect an empty MP4 file" }
        return Mp4ByteArraySource(bytes).use { inspect(it) }
    }

    internal fun inspect(source: Mp4ByteSource): LocationRepresentationInfo {
        val root = parseBoxes(source, 0, source.size)
        val quickTime = mutableListOf<QuickTimeLocation>()
        val genericKeys = linkedSetOf<String>()
        var threeGpp = false

        fun visit(box: Mp4Box, path: List<String>) {
            val currentPath = path + box.type
            if (currentPath.takeLast(3) == listOf("moov", "udta", "©xyz")) {
                quickTime += QuickTimeLocation(
                    boxBytes = source.readAtom(box.start, (box.end - box.start).toInt()),
                    payload = source.readAtom(box.payloadStart, (box.end - box.payloadStart).toInt()),
                )
            }
            // 3GPP LocationInformation lives at moov/udta/loci. It is only ever a mistranslation
            // artifact, never a camera-written tag on these sources — record its presence so the
            // contract suite can assert the cut never invents it.
            if (currentPath.takeLast(3) == listOf("moov", "udta", "loci")) threeGpp = true
            if (box.type == "meta") {
                genericKeys += readLocationKeys(source, box)
            }
            childrenOf(source, box).forEach { child -> visit(child, currentPath) }
        }

        root.forEach { visit(it, emptyList()) }
        return LocationRepresentationInfo(quickTime, genericKeys, threeGpp)
    }

    private fun readLocationKeys(source: Mp4ByteSource, meta: Mp4Box): Set<String> {
        // A meta box is a FullBox: version/flags precede its child boxes.
        if (meta.payloadStart + 4 > meta.end) return emptySet()
        val children = childrenOf(source, meta)
        val keys = children.firstOrNull { it.type == "keys" } ?: return emptySet()
        if (keys.payloadStart + 8 > keys.end) return emptySet()

        val entryCount = source.readUInt32(keys.payloadStart + 4)
        if (entryCount > Int.MAX_VALUE) {
            throw IllegalArgumentException("MP4 keys entry count is too large: $entryCount")
        }
        var cursor = keys.payloadStart + 8
        val found = linkedSetOf<String>()
        repeat(entryCount.toInt()) {
            if (cursor + 8 > keys.end) {
                throw IllegalArgumentException("Truncated MP4 mdta key entry")
            }
            val entrySize = source.readUInt32(cursor).toIntChecked("mdta key size")
            if (entrySize < 8 || cursor + entrySize > keys.end) {
                throw IllegalArgumentException("Invalid MP4 mdta key size: $entrySize")
            }
            val namespace = source.decodeType(cursor + 4)
            val key = String(
                source.readAtom(cursor + 8, entrySize - 8),
                StandardCharsets.UTF_8,
            )
            if (namespace == "mdta" && (key == "location" || key == "location-eng")) {
                found += key
            }
            cursor += entrySize
        }
        return found
    }

    internal fun childrenOf(source: Mp4ByteSource, box: Mp4Box): List<Mp4Box> {
        if (box.type !in CONTAINER_TYPES) return emptyList()
        val childStart = if (box.type == "meta") metaChildStart(source, box) else box.payloadStart
        if (childStart > box.end) {
            throw IllegalArgumentException("MP4 ${box.type} box has no room for children")
        }
        return parseBoxes(source, childStart, box.end)
    }

    private fun metaChildStart(source: Mp4ByteSource, box: Mp4Box): Long {
        // Both forms occur in real files: QuickTime metadata commonly puts `hdlr` at
        // the payload start, while ISO FullBox metadata puts version/flags there first.
        return if (looksLikeBoxHeader(source, box.payloadStart, box.end)) {
            box.payloadStart
        } else {
            box.payloadStart + 4
        }
    }

    private fun looksLikeBoxHeader(source: Mp4ByteSource, start: Long, end: Long): Boolean {
        if (start < 0 || start + 8 > end) return false
        val size = source.readUInt32(start)
        val type = source.decodeType(start + 4)
        if (!type.all { it.code in 0x20..0x7e || it == '©' }) return false
        return when (size) {
            0L -> true
            1L -> start + 16 <= end && source.readUInt64(start + 8) <= end - start
            else -> size >= 8L && size <= end - start
        }
    }

    internal fun parseBoxes(source: Mp4ByteSource, start: Long, end: Long): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var cursor = start
        while (cursor < end) {
            if (end - cursor < 8) {
                throw IllegalArgumentException("Truncated MP4 box header at offset $cursor")
            }
            val size32 = source.readUInt32(cursor)
            val type = source.decodeType(cursor + 4)
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    // A zero-sized box extends to its containing box's end.
                    headerSize = 8
                    boxSize = end - cursor
                }
                1L -> {
                    if (end - cursor < 16) {
                        throw IllegalArgumentException("Truncated extended MP4 box header at offset $cursor")
                    }
                    headerSize = 16
                    boxSize = source.readUInt64(cursor + 8)
                }
                else -> {
                    headerSize = 8
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || boxSize > end - cursor) {
                throw IllegalArgumentException("Invalid MP4 box size $boxSize for $type at $cursor")
            }
            val boxEnd = cursor + boxSize
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

    /** A parsed box. All positions are file-wide [Long] offsets, never `Int`. */
    internal data class Mp4Box(
        val type: String,
        val start: Long,
        val headerSize: Int,
        val payloadStart: Long,
        val end: Long,
    )

    internal val CONTAINER_TYPES = setOf(
        "moov", "udta", "meta", "ilst", "trak", "mdia", "minf", "stbl", "dinf",
        "edts", "mvex", "moof", "traf", "mfra", "skip", "ipro", "sinf", "schi", "wave",
    )
}

/**
 * Random-access byte window over an MP4's bytes. Positions are file-wide [Long]
 * offsets so files beyond 2 GB are addressable; only single metadata atoms are ever
 * materialized as [ByteArray].
 */
internal interface Mp4ByteSource : java.io.Closeable {
    val size: Long
    fun readUInt32(offset: Long): Long
    fun readUInt64(offset: Long): Long
    fun readAtom(offset: Long, length: Int): ByteArray
    fun decodeType(offset: Long): String
}

/** In-memory source for bytes that are genuinely already buffered (tests, small atoms). */
internal class Mp4ByteArraySource(private val bytes: ByteArray) : Mp4ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun readUInt32(offset: Long): Long {
        require(offset in 0..bytes.size - 4) { "MP4 u32 read out of range: $offset" }
        return Mp4LocationMetadata.readUInt32(bytes, offset.toInt())
    }

    override fun readUInt64(offset: Long): Long {
        require(offset in 0..bytes.size - 8) { "MP4 u64 read out of range: $offset" }
        return Mp4LocationMetadata.readUInt64(bytes, offset.toInt())
    }

    override fun readAtom(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
            "MP4 atom read out of range: offset=$offset length=$length size=${bytes.size}"
        }
        return bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
    }

    override fun decodeType(offset: Long): String {
        require(offset in 0..bytes.size - 4) { "MP4 type read out of range: $offset" }
        return Mp4LocationMetadata.decodeType(bytes, offset.toInt())
    }

    override fun close() = Unit
}

/**
 * Seekable source over a real file. Reads are tiny (headers) or bounded to one
 * metadata atom ([MAX_ATOM_BYTES]); the file is never buffered as a whole, which is
 * what lets probing and finalization survive files far larger than the app heap.
 */
internal class Mp4FileByteSource(private val file: File) : Mp4ByteSource {
    private val raf = RandomAccessFile(file, "r")

    override val size: Long get() = raf.length()

    override fun readUInt32(offset: Long): Long {
        require(offset in 0..size - 4) { "MP4 u32 read out of range: $offset in ${file.name}" }
        raf.seek(offset)
        return ((raf.readByte().toLong() and 0xff) shl 24) or
            ((raf.readByte().toLong() and 0xff) shl 16) or
            ((raf.readByte().toLong() and 0xff) shl 8) or
            (raf.readByte().toLong() and 0xff)
    }

    override fun readUInt64(offset: Long): Long {
        require(offset in 0..size - 8) { "MP4 u64 read out of range: $offset in ${file.name}" }
        raf.seek(offset)
        val value = raf.readLong()
        require(value >= 0L) { "MP4 box size exceeds supported JVM range in ${file.name}" }
        return value
    }

    override fun readAtom(offset: Long, length: Int): ByteArray {
        require(length in 0..MAX_METADATA_ATOM_BYTES) {
            "MP4 atom of $length bytes at $offset in ${file.name} is implausibly large"
        }
        require(offset >= 0 && offset + length <= size) {
            "MP4 atom read out of range: offset=$offset length=$length size=$size"
        }
        return ByteArray(length).also { buffer ->
            raf.seek(offset)
            raf.readFully(buffer)
        }
    }

    override fun decodeType(offset: Long): String =
        String(readAtom(offset, 4), StandardCharsets.ISO_8859_1)

    override fun close() = raf.close()
}
