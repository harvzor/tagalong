package dev.tagalong.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Regression guard for the whole-file buffering removed by the streaming rework.
 *
 * The `:engine` unit-test JVM is pinned to a phone-sized heap (`maxHeapSize = "256m"` in
 * `engine/build.gradle.kts`) because the production app runs on the stock ~256 MB Android
 * per-app heap and declares no `largeHeap`. Before the rework, `preserve` and `inspect(File)`
 * called `File.readBytes()`, so each test below died with `OutOfMemoryError` at
 * `Mp4LocationFinalizer.kt:21` the way a real 4K cut dies on a real phone. A streaming
 * implementation never holds more than a metadata atom and a copy buffer, so all three
 * pass after it.
 */
class Mp4LocationHeapBudgetTest {

    @Rule @JvmField
    val tempFolder = TemporaryFolder()

    private val sampleVideos = File(
        System.getProperty("tagalong.sampleVideos")
            ?: error("tagalong.sampleVideos system property not set (see engine/build.gradle.kts)"),
    )

    private val heapBudget = Runtime.getRuntime().maxMemory()

    /** The pin is the test. If the module-wide 256m ever gets raised, fail loudly here. */
    private fun assertPhoneSizedHeap() {
        assertTrue(
            "unit-test heap is ${heapBudget / (1 shl 20)} MB; these tests require the " +
                "phone-sized maxHeapSize = \"256m\" pin from engine/build.gradle.kts",
            heapBudget in (192L shl 20)..(320L shl 20),
        )
    }

    @Test
    fun preserveCompletesWhenSourceAndOutputExceedTheHeapBudget() {
        assertPhoneSizedHeap()
        val beyondHeap = heapBudget + (32L shl 20)

        // Pixel as source, Xiaomi as output: their ©xyz payloads differ, so this exercises
        // the full splice-and-patch rewrite rather than the already-correct early return.
        val source = tempFolder.newFile("heap-budget-source.mp4")
        File(sampleVideos, "google-pixel-10a.mp4").paddedCopyTo(source, beyondHeap)
        val output = tempFolder.newFile("heap-budget-output.mp4")
        File(sampleVideos, "xiaomi-poco-x5.mp4").paddedCopyTo(output, beyondHeap)

        Mp4LocationFinalizer.preserve(source, output)

        val sourceInfo = Mp4LocationMetadata.inspect(source)
        val outputInfo = Mp4LocationMetadata.inspect(output)
        assertTrue("source fixture must carry the QuickTime atom", sourceInfo.hasQuickTime)
        assertTrue("finalized output must carry the QuickTime atom", outputInfo.hasQuickTime)
        assertTrue(
            "finalized output must retain the source ©xyz payload byte-for-byte",
            sourceInfo.quickTimePayloadsEqual(outputInfo),
        )
        assertArrayEquals(
            "output ©xyz must be the source payload, not the output's own",
            Mp4LocationMetadata.inspect(File(sampleVideos, "google-pixel-10a.mp4")).quickTimePayload,
            outputInfo.quickTimePayload,
        )
    }

    @Test
    fun inspectReportsRepresentationWithoutBufferingTheWholeFile() {
        assertPhoneSizedHeap()
        val padded = tempFolder.newFile("heap-budget-probe.mp4")
        File(sampleVideos, "google-pixel-10a.mp4")
            .paddedCopyTo(padded, heapBudget + (32L shl 20))

        val paddedInfo = Mp4LocationMetadata.inspect(padded)
        val referenceInfo = Mp4LocationMetadata.inspect(File(sampleVideos, "google-pixel-10a.mp4"))

        assertEquals(referenceInfo.representation, paddedInfo.representation)
        assertArrayEquals(referenceInfo.quickTimePayload, paddedInfo.quickTimePayload)
    }

    /**
     * Correctness net for the in-place patch pass, with an independently parsed oracle.
     *
     * The corpus samples keep `moov` after `mdat`, so a splice inside `moov` shifts no
     * chunk offsets. FFmpeg's production outputs use `+faststart` (`moov` first), where
     * every `stco` entry must gain exactly the size delta. This tiny synthetic file pins
     * that arithmetic: `moov` is first, two chunks point into `mdat`, and the expected
     * post-patch sizes/offsets are computed from the known layout — no engine parser is
     * used for the assertions.
     */
    @Test
    fun preserveShiftsFastStartChunkOffsetsAndEnclosingBoxSizes() {
        val oldPayload = "\u0000\u0011\u0015\u00c7+0.0000+0.0000/".toByteArray(StandardCharsets.ISO_8859_1)
        val newPayload = "\u0000\u0011\u0015\u00c7+52.5562+13.3418/".toByteArray(StandardCharsets.ISO_8859_1)
        val delta = newPayload.size - oldPayload.size
        assertTrue("fixture must shrink or grow the atom to exercise the patch pass", delta != 0)

        val source = tempFolder.newFile("faststart-source.mp4")
        source.writeBytes(box("moov", box("udta", box("\u00a9xyz", newPayload))))

        // Layout: [ftyp][moov[udta[©xyz(old)] , trak[mdia[minf[stbl[stco]]]]]][mdat]
        val ftypSize = 16L
        val xyzSize = 8 + oldPayload.size
        val udtaSize = 8 + xyzSize
        val stcoEntries = longArrayOf(0L, 500L) // patched below once mdat's position is known
        val stcoSize = 8 + 8 + 4 * stcoEntries.size
        val stblSize = 8 + stcoSize
        val minfSize = 8 + stblSize
        val mdiaSize = 8 + minfSize
        val trakSize = 8 + mdiaSize
        val moovSize = 8 + udtaSize + trakSize
        val mdatPayloadStart = ftypSize + moovSize + 8
        stcoEntries[0] = mdatPayloadStart
        stcoEntries[1] = mdatPayloadStart + 500

        val stco = fullBox(
            "stco",
            int32(stcoEntries.size) + ByteArray(stcoEntries.size * 4).also { arr ->
                stcoEntries.forEachIndexed { index, value -> int32(value.toInt()).copyInto(arr, index * 4) }
            },
        )
        val stbl = box("stbl", stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", minf)
        val trak = box("trak", mdia)
        val udta = box("udta", box("\u00a9xyz", oldPayload))
        val moov = box("moov", udta + trak)
        val mdat = box("mdat", ByteArray(1000) { 0x5a })
        assertEquals(mdatPayloadStart, ftypSize + moovSize + 8)

        val output = tempFolder.newFile("faststart-output.mp4")
        output.writeBytes(box("ftyp", "isomiso2".toByteArray(StandardCharsets.ISO_8859_1)) + moov + mdat)

        Mp4LocationFinalizer.preserve(source, output)

        // Independently re-parse the result.
        val bytes = output.readBytes()
        val root = parseRootBoxes(bytes)
        val moovBox = root.single { it.type == "moov" }
        val mdatBox = root.single { it.type == "mdat" }
        val udtaBox = childrenOf(bytes, moovBox).single { it.type == "udta" }
        val xyzBoxes = childrenOf(bytes, udtaBox).filter { it.type == "\u00a9xyz" }

        assertEquals("exactly one QuickTime location atom after the rewrite", 1, xyzBoxes.size)
        assertArrayEquals(
            "source payload must survive verbatim",
            newPayload,
            bytes.copyOfRange(xyzBoxes.single().payloadStart, xyzBoxes.single().end),
        )
        assertEquals("moov size must grow by the exact delta", (moovSize + delta).toLong(), moovBox.size)
        assertEquals("udta size must grow by the exact delta", (udtaSize + delta).toLong(), udtaBox.size)

        val stblBox = childrenOf(bytes, childrenOf(bytes, childrenOf(bytes, childrenOf(bytes, moovBox).single { it.type == "trak" }).single { it.type == "mdia" }).single { it.type == "minf" }).single { it.type == "stbl" }
        val stcoBox = childrenOf(bytes, stblBox).single { it.type == "stco" }
        val entriesStart = stcoBox.payloadStart + 8
        val updated = stcoEntries.mapIndexed { index, original ->
            readU32(bytes, entriesStart + index * 4)
        }
        assertEquals(
            "every faststart chunk offset must shift by the exact delta",
            stcoEntries.map { it + delta },
            updated,
        )
        // Chunk data itself must still be intact at the shifted positions.
        updated.forEach { offset ->
            assertEquals(0x5a.toByte(), bytes[offset.toInt()])
        }
        assertTrue("mdat payload must start where the shifted chunks expect it", mdatBox.payloadStart <= updated.first())
    }

    // ---- minimal independent MP4 readers (test oracle; deliberately not the engine's) ----

    private data class TestBox(val type: String, val start: Int, val payloadStart: Int, val end: Int) {
        val size: Long get() = (end - start).toLong()
    }

    private fun parseRootBoxes(bytes: ByteArray): List<TestBox> = parseBoxes(bytes, 0, bytes.size)

    private fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<TestBox> {
        val boxes = mutableListOf<TestBox>()
        var cursor = start
        while (cursor < end) {
            val size = readU32(bytes, cursor)
            val type = String(bytes, cursor + 4, 4, StandardCharsets.ISO_8859_1)
            val boxEnd = cursor + size.toInt()
            boxes += TestBox(type, cursor, cursor + 8, boxEnd)
            cursor = boxEnd
        }
        return boxes
    }

    private fun childrenOf(bytes: ByteArray, parent: TestBox): List<TestBox> =
        parseBoxes(bytes, parent.payloadStart, parent.end)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun box(type: String, payload: ByteArray): ByteArray =
        int32(8 + payload.size) + type.toByteArray(StandardCharsets.ISO_8859_1) + payload

    private fun fullBox(type: String, payload: ByteArray): ByteArray =
        box(type, byteArrayOf(0, 0, 0, 0) + payload)

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )
}
