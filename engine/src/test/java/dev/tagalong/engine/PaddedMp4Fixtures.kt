package dev.tagalong.engine

import java.io.File
import java.io.RandomAccessFile

/**
 * Test fixtures that grow a real MP4 to a requested size by appending valid root-level
 * `free` boxes. `free` is ignored by MP4 readers and is not a container the location
 * walker descends into, so the padded file keeps the sample's exact metadata layout
 * while crossing a memory budget no streaming implementation should care about.
 *
 * Written with [RandomAccessFile] on purpose: the fixture builder must never buffer a
 * whole file itself, or the heap-budget tests would exhaust the pinned heap in test
 * setup rather than in the code under test.
 */
internal fun File.paddedCopyTo(target: File, targetBytes: Long) {
    require(targetBytes >= length()) { "Target size must be at least the source size" }
    inputStream().use { src -> target.outputStream().use(src::copyTo) }
    RandomAccessFile(target, "rw").use { raf ->
        val zeros = ByteArray(8 shl 20)
        var remaining = targetBytes - raf.length()
        while (remaining > 8) {
            // Cap each padding box at 1 GiB so its 32-bit size header stays valid.
            val payload = minOf(remaining - 8, 1L shl 30)
            val header = ByteArray(8)
            val boxSize = (payload + 8).toInt()
            header[0] = (boxSize ushr 24).toByte()
            header[1] = (boxSize ushr 16).toByte()
            header[2] = (boxSize ushr 8).toByte()
            header[3] = boxSize.toByte()
            "free".toByteArray(Charsets.ISO_8859_1).copyInto(header, 4)
            raf.seek(raf.length())
            raf.write(header)
            var written = 0L
            while (written < payload) {
                val chunk = minOf(zeros.size.toLong(), payload - written).toInt()
                raf.write(zeros, 0, chunk)
                written += chunk
            }
            remaining -= payload + 8
        }
    }
}
