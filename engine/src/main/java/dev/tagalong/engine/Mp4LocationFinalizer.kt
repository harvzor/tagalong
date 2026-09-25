package dev.tagalong.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel

/** Raised when a cut output is not a layout this narrow metadata writer can prove safe. */
class UnsupportedMp4LayoutException(message: String) : IllegalStateException(message)

/**
 * Copies the source's raw QuickTime location box into an FFmpeg output.
 *
 * FFmpeg's normalized metadata path currently emits generic `mdta/location` entries. This
 * finalizer restores the source `moov/udta/©xyz` bytes without reserializing coordinates or
 * touching encoded media packets. It is deliberately narrow: unsupported or ambiguous MP4
 * layouts fail instead of producing an output whose offsets cannot be trusted.
 *
 * The rewrite is streaming and memory stays flat regardless of file size — no stage ever
 * buffers a whole file, and file positions are [Long] throughout, so outputs beyond 2 GB
 * (routine for 4K recordings) are addressable:
 *
 * 1. a seekable descriptor pass walks the output's box tree;
 * 2. a stream-splice copy moves output → temp through [FileChannel], inserting the raw
 *    source `©xyz` bytes at the splice point;
 * 3. a patch pass fixes, in place in the temp file, only the fixed-width fields the size
 *    delta invalidates: enclosing box sizes and `stco`/`co64` chunk offsets;
 * 4. self-verification re-inspects the temp file before it replaces the output.
 *
 * Unsupported-layout failures and the temp-file-and-swap installation semantics are
 * unchanged from the original whole-file implementation.
 */
object Mp4LocationFinalizer {

    private const val TRANSFER_CHUNK = 1L shl 22 // 4 MiB per channel transfer.

    /**
     * Convenience overload: inspects the source, then finalizes. Prefer the overload that
     * takes a pre-inspected [LocationRepresentationInfo] when the caller has already probed
     * the source (the engine does, to decide location suppression) — reusing the inspected
     * result keeps source probing to a single streaming pass.
     */
    fun preserve(source: File, output: File) {
        preserve(source, output, Mp4LocationMetadata.inspect(source))
    }

    /**
     * Finalizes [output] with the source's QuickTime location, reusing a [sourceInfo] the
     * caller inspected earlier. The cut engine inspects the source once (ahead of running
     * ffmpeg, to decide whether to suppress ffmpeg's location mistranslation) and hands the
     * result here, so the source is streamed a single time rather than re-probed per stage.
     */
    fun preserve(source: File, output: File, sourceInfo: LocationRepresentationInfo) {
        require(source.absoluteFile != output.absoluteFile) { "Source and output must be different files" }
        if (!sourceInfo.hasQuickTime) return

        val outputSource = Mp4FileByteSource(output)
        try {
            val root = Mp4LocationMetadata.parseBoxes(outputSource, 0, outputSource.size)
            requireNoFragments(outputSource, root)
            val moovs = root.filter { it.type == "moov" }
            if (moovs.size != 1) {
                throw UnsupportedMp4LayoutException("Expected exactly one output moov box, found ${moovs.size}")
            }
            val moov = moovs.single()
            val moovChildren = Mp4LocationMetadata.childrenOf(outputSource, moov)
            val udtas = moovChildren.filter { it.type == "udta" }
            if (udtas.size > 1) {
                throw UnsupportedMp4LayoutException("Output contains multiple moov/udta boxes")
            }

            val udta = udtas.singleOrNull()
            val existingBoxes = udta?.let { parent ->
                Mp4LocationMetadata.childrenOf(outputSource, parent).filter { it.type == "©xyz" }
            }.orEmpty()

            // Avoid rewriting an already-correct output. This also prevents duplicate location
            // atoms if a future FFmpeg build learns to emit the QuickTime form itself.
            val existingInfo = runCatching { Mp4LocationMetadata.inspect(outputSource) }.getOrNull()
            if (existingInfo != null && existingInfo.quickTimePayloadsEqual(sourceInfo)) return

            val replacement = concatenate(sourceInfo.quickTimeLocations.map { it.boxBytes })
            val changeStart: Long
            val changeEnd: Long
            val replacementBytes: ByteArray
            if (existingBoxes.isNotEmpty()) {
                val first = existingBoxes.first()
                val last = existingBoxes.last()
                val directChildren = Mp4LocationMetadata.childrenOf(outputSource, requireNotNull(udta))
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

            val delta = replacementBytes.size.toLong() - (changeEnd - changeStart)
            val parent = output.parentFile
                ?: throw UnsupportedMp4LayoutException("Output has no parent directory")
            if (!parent.exists() && !parent.mkdirs()) {
                throw UnsupportedMp4LayoutException("Could not create output directory ${parent.absolutePath}")
            }
            val temporary = File(parent, ".${output.name}.xyz-${System.nanoTime()}.tmp")
            try {
                streamSplice(output, temporary, changeStart, changeEnd, replacementBytes)
                if (delta != 0L) {
                    RandomAccessFile(temporary, "rw").use { patch ->
                        updateAncestorSizes(
                            patch,
                            ancestors = listOfNotNull(udta, moov),
                            changeEnd = changeEnd,
                            delta = delta,
                        )
                        updateChunkOffsets(patch, outputSource, root, changeStart, changeEnd, delta)
                    }
                }

                val rewrittenInfo = Mp4LocationMetadata.inspect(temporary)
                if (!rewrittenInfo.quickTimePayloadsEqual(sourceInfo)) {
                    throw UnsupportedMp4LayoutException(
                        "Finalized output did not retain the source ©xyz payload " +
                            "(source boxes=${sourceInfo.quickTimeLocations.size}, " +
                            "output boxes=${rewrittenInfo.quickTimeLocations.size})",
                    )
                }
                install(temporary, output)
            } finally {
                temporary.delete()
            }
        } finally {
            outputSource.close()
        }
    }

    /**
     * Copies [original] to [temporary] through file channels, skipping the
     * `[changeStart, changeEnd)` range and inserting [replacement] in its place.
     * Memory use is one transfer chunk regardless of file size.
     */
    private fun streamSplice(
        original: File,
        temporary: File,
        changeStart: Long,
        changeEnd: Long,
        replacement: ByteArray,
    ) {
        FileChannel.open(original.toPath(), StandardOpenOption.READ).use { input ->
            FileChannel.open(
                temporary.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { target ->
                copyRange(input, target, 0L, changeStart)
                var buffer = ByteBuffer.wrap(replacement)
                while (buffer.hasRemaining()) {
                    if (target.write(buffer) <= 0) {
                        throw UnsupportedMp4LayoutException("Could not write replacement location bytes")
                    }
                }
                copyRange(input, target, changeEnd, input.size())
            }
        }
    }

    private fun copyRange(input: FileChannel, target: FileChannel, from: Long, to: Long) {
        var position = from
        while (position < to) {
            val remaining = to - position
            // transferTo reports its own source position argument as absolute and may
            // write fewer bytes than requested; loop until the region is fully copied.
            val transferred = input.transferTo(position, minOf(TRANSFER_CHUNK, remaining), target)
            if (transferred <= 0L) {
                throw UnsupportedMp4LayoutException("MP4 copy-through stalled at offset $position")
            }
            position += transferred
        }
    }

    private fun requireNoFragments(
        source: Mp4ByteSource,
        boxes: List<Mp4LocationMetadata.Mp4Box>,
    ) {
        fun visit(box: Mp4LocationMetadata.Mp4Box) {
            if (box.type == "moof" || box.type == "mfra") {
                throw UnsupportedMp4LayoutException("Fragmented MP4 layouts are not supported")
            }
            Mp4LocationMetadata.childrenOf(source, box).forEach(::visit)
        }
        boxes.forEach(::visit)
    }

    private fun updateAncestorSizes(
        patch: RandomAccessFile,
        ancestors: List<Mp4LocationMetadata.Mp4Box>,
        changeEnd: Long,
        delta: Long,
    ) {
        ancestors.forEach { box ->
            val newSize = (box.end - box.start) + delta
            val newStart = shiftedPosition(box.start, changeEnd, delta)
            writeBoxSize(patch, newStart, box.headerSize, newSize)
        }
    }

    private fun updateChunkOffsets(
        patch: RandomAccessFile,
        original: Mp4ByteSource,
        originalRoot: List<Mp4LocationMetadata.Mp4Box>,
        changeStart: Long,
        changeEnd: Long,
        delta: Long,
    ) {
        // Chunk offsets are the only absolute file positions inside a non-fragmented MP4.
        // Values are read from the untouched original file and written, adjusted, into the
        // spliced temp file at each box's post-splice position.
        allBoxes(original, originalRoot).forEach { box ->
            if (box.type != "stco" && box.type != "co64") return@forEach
            val entryCountOffset = box.payloadStart + 4
            if (entryCountOffset + 4 > box.end) {
                throw UnsupportedMp4LayoutException("Truncated ${box.type} box")
            }
            val count = original.readUInt32(entryCountOffset)
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
                val value = if (entrySize == 4) {
                    original.readUInt32(oldOffset)
                } else {
                    original.readUInt64(oldOffset)
                }
                if (value in changeStart until changeEnd) {
                    throw UnsupportedMp4LayoutException("${box.type} points into the rewritten MP4 range")
                }
                if (value >= changeEnd) {
                    val adjusted = value + delta
                    if (adjusted < 0L) {
                        throw UnsupportedMp4LayoutException("${box.type} offset became negative")
                    }
                    if (entrySize == 4 && adjusted > 0xffff_ffffL) {
                        throw UnsupportedMp4LayoutException("stco offset overflow requires co64 conversion")
                    }
                    val newOffset = entriesStart + index * entrySize
                    patch.seek(newOffset)
                    if (entrySize == 4) {
                        patch.writeInt(adjusted.toInt())
                    } else {
                        patch.writeLong(adjusted)
                    }
                }
            }
        }
    }

    private fun allBoxes(
        source: Mp4ByteSource,
        roots: List<Mp4LocationMetadata.Mp4Box>,
    ): List<Mp4LocationMetadata.Mp4Box> {
        val result = mutableListOf<Mp4LocationMetadata.Mp4Box>()
        fun visit(box: Mp4LocationMetadata.Mp4Box) {
            result += box
            Mp4LocationMetadata.childrenOf(source, box).forEach(::visit)
        }
        roots.forEach(::visit)
        return result
    }

    private fun shiftedPosition(position: Long, changeEnd: Long, delta: Long): Long {
        val shifted = position + if (position >= changeEnd) delta else 0L
        require(shifted >= 0L) { "MP4 position became negative after rewrite" }
        return shifted
    }

    private fun writeBoxSize(patch: RandomAccessFile, start: Long, headerSize: Int, size: Long) {
        require(size >= headerSize) { "Invalid rewritten MP4 box size $size" }
        patch.seek(start)
        if (headerSize == 8) {
            if (size > 0xffff_ffffL) {
                throw UnsupportedMp4LayoutException("32-bit MP4 box size overflow")
            }
            patch.writeInt(size.toInt())
        } else {
            patch.writeInt(1)
            patch.seek(start + 8)
            patch.writeLong(size)
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

    /** Replaces the FFmpeg output with the verified temp file, as the original swap did. */
    private fun install(temporary: File, output: File) {
        // Files.move with REPLACE_EXISTING covers the delete+rename of the original
        // implementation in one operation on the same filesystem (cacheDir).
        try {
            Files.move(temporary.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: java.io.IOException) {
            throw UnsupportedMp4LayoutException("Could not install finalized output ${output.name}: ${failure.message}")
        }
    }
}
