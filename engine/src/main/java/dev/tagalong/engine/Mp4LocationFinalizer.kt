package dev.tagalong.engine

import java.io.File
import java.nio.charset.StandardCharsets

/** Raised when a cut output is not a layout this narrow metadata writer can prove safe. */
class UnsupportedMp4LayoutException(message: String) : IllegalStateException(message)

/**
 * Copies the source's raw QuickTime location box into an FFmpeg output.
 *
 * FFmpeg's normalized metadata path currently emits generic `mdta/location` entries. This
 * finalizer restores the source `moov/udta/©xyz` bytes without reserializing coordinates or
 * touching encoded media packets. It is deliberately narrow: unsupported or ambiguous MP4
 * layouts fail instead of producing an output whose offsets cannot be trusted.
 */
object Mp4LocationFinalizer {

    fun preserve(source: File, output: File) {
        require(source.absoluteFile != output.absoluteFile) { "Source and output must be different files" }
        val sourceBytes = source.readBytes()
        val sourceInfo = Mp4LocationMetadata.inspect(sourceBytes)
        if (!sourceInfo.hasQuickTime) return

        val outputBytes = output.readBytes()
        val root = Mp4LocationMetadata.parseBoxes(outputBytes, 0, outputBytes.size)
        requireNoFragments(root, outputBytes)
        val moovs = root.filter { it.type == "moov" }
        if (moovs.size != 1) {
            throw UnsupportedMp4LayoutException("Expected exactly one output moov box, found ${moovs.size}")
        }
        val moov = moovs.single()
        val moovChildren = Mp4LocationMetadata.childrenOf(moov, outputBytes)
        val udtas = moovChildren.filter { it.type == "udta" }
        if (udtas.size > 1) {
            throw UnsupportedMp4LayoutException("Output contains multiple moov/udta boxes")
        }

        val sourceBoxes = sourceInfo.quickTimeLocations.map { it.boxBytes }
        val udta = udtas.singleOrNull()
        val existingBoxes = udta?.let { parent ->
            Mp4LocationMetadata.childrenOf(parent, outputBytes).filter { it.type == "©xyz" }
        }.orEmpty()

        // Avoid rewriting an already-correct output. This also prevents duplicate location
        // atoms if a future FFmpeg build learns to emit the QuickTime form itself.
        val existingInfo = runCatching { Mp4LocationMetadata.inspect(outputBytes) }.getOrNull()
        if (existingInfo != null && existingInfo.quickTimePayloadsEqual(sourceInfo)) return

        val replacement = concatenate(sourceBoxes)
        val changeStart: Int
        val changeEnd: Int
        val replacementBytes: ByteArray
        if (existingBoxes.isNotEmpty()) {
            val first = existingBoxes.first()
            val last = existingBoxes.last()
            val directChildren = Mp4LocationMetadata.childrenOf(requireNotNull(udta), outputBytes)
            val firstIndex = directChildren.indexOf(first)
            val lastIndex = directChildren.indexOf(last)
            if (firstIndex < 0 || lastIndex < firstIndex ||
                directChildren.subList(firstIndex, lastIndex + 1).any { it.type != "©xyz" }
            ) {
                throw UnsupportedMp4LayoutException("Output QuickTime location boxes are not contiguous")
            }
            changeStart = first.start
            changeEnd = last.end
            replacementBytes = replacement
        } else if (udta != null) {
            changeStart = udta.end
            changeEnd = udta.end
            replacementBytes = replacement
        } else {
            // FFmpeg normally creates udta for MP4 metadata. Supporting its absence is
            // inexpensive and keeps the finalizer valid for minimal MP4 outputs.
            changeStart = moov.end
            changeEnd = moov.end
            replacementBytes = standardBox("udta", replacement)
        }

        val delta = replacementBytes.size.toLong() - (changeEnd - changeStart).toLong()
        val rewritten = replaceRange(outputBytes, changeStart, changeEnd, replacementBytes)
        updateAncestorSizes(
            rewritten,
            ancestors = listOfNotNull(udta, moov),
            changeEnd = changeEnd,
            delta = delta,
        )
        updateChunkOffsets(rewritten, root, outputBytes, changeStart, changeEnd, delta)

        val rewrittenInfo = Mp4LocationMetadata.inspect(rewritten)
        if (!rewrittenInfo.quickTimePayloadsEqual(sourceInfo)) {
            throw UnsupportedMp4LayoutException(
                "Finalized output did not retain the source ©xyz payload " +
                    "(source boxes=${sourceInfo.quickTimeLocations.size}, " +
                    "output boxes=${rewrittenInfo.quickTimeLocations.size})",
            )
        }
        writeReplacement(output, rewritten)
    }

    private fun requireNoFragments(
        boxes: List<Mp4LocationMetadata.Mp4Box>,
        bytes: ByteArray,
    ) {
        fun visit(box: Mp4LocationMetadata.Mp4Box) {
            if (box.type == "moof" || box.type == "mfra") {
                throw UnsupportedMp4LayoutException("Fragmented MP4 layouts are not supported")
            }
            Mp4LocationMetadata.childrenOf(box, bytes).forEach(::visit)
        }
        boxes.forEach(::visit)
    }

    private fun updateAncestorSizes(
        bytes: ByteArray,
        ancestors: List<Mp4LocationMetadata.Mp4Box>,
        changeEnd: Int,
        delta: Long,
    ) {
        if (delta == 0L) return
        ancestors.forEach { box ->
            val newSize = (box.end - box.start).toLong() + delta
            val newStart = shiftedPosition(box.start, changeEnd, delta)
            writeBoxSize(bytes, newStart, box.headerSize, newSize)
        }
    }

    private fun updateChunkOffsets(
        bytes: ByteArray,
        originalRoot: List<Mp4LocationMetadata.Mp4Box>,
        originalBytes: ByteArray,
        changeStart: Int,
        changeEnd: Int,
        delta: Long,
    ) {
        if (delta == 0L) return
        // The original tree and bytes provide stable positions and values; [bytes] is the
        // rewritten output into which adjusted entries are written.
        allBoxes(originalRoot, originalBytes).forEach { box ->
            if (box.type != "stco" && box.type != "co64") return@forEach
            val entryCountOffset = box.payloadStart + 4
            if (entryCountOffset + 4 > box.end) {
                throw UnsupportedMp4LayoutException("Truncated ${box.type} box")
            }
            val count = Mp4LocationMetadata.readUInt32(originalBytes, entryCountOffset)
            if (count > Int.MAX_VALUE) {
                throw UnsupportedMp4LayoutException("${box.type} entry count is too large")
            }
            val entrySize = if (box.type == "stco") 4 else 8
            val oldPayloadEnd = box.payloadStart + 8L + count * entrySize
            if (oldPayloadEnd > box.end) {
                throw UnsupportedMp4LayoutException("Truncated ${box.type} entries")
            }
            val boxNewStart = shiftedPosition(box.start, changeEnd, delta)
            val entriesStart = boxNewStart + (box.payloadStart - box.start) + 8
            repeat(count.toInt()) { index ->
                val oldOffset = box.payloadStart + 8 + index * entrySize
                val newOffset = entriesStart + index * entrySize
                val value = if (entrySize == 4) {
                    Mp4LocationMetadata.readUInt32(originalBytes, oldOffset)
                } else {
                    Mp4LocationMetadata.readUInt64(originalBytes, oldOffset)
                }
                if (value in changeStart.toLong() until changeEnd.toLong()) {
                    throw UnsupportedMp4LayoutException("${box.type} points into the rewritten MP4 range")
                }
                if (value >= changeEnd.toLong()) {
                    val adjusted = value + delta
                    if (adjusted < 0L) {
                        throw UnsupportedMp4LayoutException("${box.type} offset became negative")
                    }
                    if (entrySize == 4 && adjusted > 0xffff_ffffL) {
                        throw UnsupportedMp4LayoutException("stco offset overflow requires co64 conversion")
                    }
                    if (entrySize == 4) writeUInt32(bytes, newOffset, adjusted)
                    else writeUInt64(bytes, newOffset, adjusted)
                }
            }
        }
    }

    private fun allBoxes(
        roots: List<Mp4LocationMetadata.Mp4Box>,
        bytes: ByteArray?,
    ): List<Mp4LocationMetadata.Mp4Box> {
        val result = mutableListOf<Mp4LocationMetadata.Mp4Box>()
        fun visit(box: Mp4LocationMetadata.Mp4Box) {
            result += box
            if (bytes != null) {
                Mp4LocationMetadata.childrenOf(box, bytes).forEach(::visit)
            }
        }
        roots.forEach(::visit)
        return result
    }

    private fun replaceRange(
        source: ByteArray,
        start: Int,
        end: Int,
        replacement: ByteArray,
    ): ByteArray {
        val newLength = source.size.toLong() - (end - start).toLong() + replacement.size
        if (newLength !in 0..Int.MAX_VALUE) {
            throw UnsupportedMp4LayoutException("Rewritten MP4 is too large")
        }
        return ByteArray(newLength.toInt()).also { output ->
            source.copyInto(output, 0, 0, start)
            replacement.copyInto(output, start)
            source.copyInto(output, start + replacement.size, end, source.size)
        }
    }

    private fun shiftedPosition(position: Int, changeEnd: Int, delta: Long): Int {
        val shifted = position.toLong() + if (position >= changeEnd) delta else 0L
        if (shifted !in 0..Int.MAX_VALUE) {
            throw UnsupportedMp4LayoutException("MP4 position became out of range")
        }
        return shifted.toInt()
    }

    private fun writeBoxSize(bytes: ByteArray, start: Int, headerSize: Int, size: Long) {
        require(size >= headerSize) { "Invalid rewritten MP4 box size $size" }
        if (headerSize == 8) {
            if (size > 0xffff_ffffL) {
                throw UnsupportedMp4LayoutException("32-bit MP4 box size overflow")
            }
            writeUInt32(bytes, start, size)
        } else {
            writeUInt32(bytes, start, 1)
            writeUInt64(bytes, start + 8, size)
        }
    }

    private fun standardBox(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        require(typeBytes.size == 4)
        val size = 8L + payload.size
        if (size > 0xffff_ffffL) throw UnsupportedMp4LayoutException("New udta box is too large")
        return ByteArray(size.toInt()).also { result ->
            writeUInt32(result, 0, size)
            typeBytes.copyInto(result, 4)
            payload.copyInto(result, 8)
        }
    }

    private fun concatenate(parts: List<ByteArray>): ByteArray {
        val length = parts.sumOf { it.size.toLong() }
        if (length > Int.MAX_VALUE) throw UnsupportedMp4LayoutException("Location boxes are too large")
        return ByteArray(length.toInt()).also { result ->
            var cursor = 0
            parts.forEach { part ->
                part.copyInto(result, cursor)
                cursor += part.size
            }
        }
    }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        require(value in 0..0xffff_ffffL)
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun writeUInt64(bytes: ByteArray, offset: Int, value: Long) {
        require(value >= 0L)
        repeat(8) { index ->
            bytes[offset + index] = (value ushr ((7 - index) * 8)).toByte()
        }
    }

    private fun writeReplacement(output: File, bytes: ByteArray) {
        val parent = output.parentFile ?: throw UnsupportedMp4LayoutException("Output has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw UnsupportedMp4LayoutException("Could not create output directory ${parent.absolutePath}")
        }
        val temporary = File(parent, ".${output.name}.xyz-${System.nanoTime()}.tmp")
        try {
            temporary.writeBytes(bytes)
            if (!output.delete() && output.exists()) {
                throw UnsupportedMp4LayoutException("Could not replace FFmpeg output ${output.name}")
            }
            if (!temporary.renameTo(output)) {
                throw UnsupportedMp4LayoutException("Could not install finalized output ${output.name}")
            }
        } finally {
            temporary.delete()
        }
    }
}
