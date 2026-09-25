package dev.tagalong.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Host-side verification of the 3GPP `loci` probe (change cut-command-honesty, task 3.1).
 *
 * The same assertions exist in the instrumented [Mp4LocationMetadataTest], but those only
 * run on-device. This host test exercises the pure box-walk logic on synthetic bytes so the
 * probe is proven before the instrumented suite is the gate.
 */
class ThreeGppLociProbeTest {

    @Test
    fun detectsLociOnlyUnderMoovUdta() {
        val withLoci = Mp4LocationMetadata.inspect(moovWithUdtaChildren(box("loci", ByteArray(16))))
        assertTrue("a loci box under moov/udta must be recorded", withLoci.hasThreeGppLocationBox)

        val withoutLoci = Mp4LocationMetadata.inspect(moovWithUdtaChildren(box("free", ByteArray(16))))
        assertFalse("an unrelated udta child must not be read as a location box", withoutLoci.hasThreeGppLocationBox)
    }

    @Test
    fun ignoresLociOutsideTheMoovUdtaPath() {
        // A stray 'loci' fourcc that is not at moov/udta/loci is data, not the 3GPP tag.
        val loose = box("moov", box("free", box("loci", ByteArray(16))))
        assertFalse(
            "a loci box not at moov/udta/loci must not count",
            Mp4LocationMetadata.inspect(loose).hasThreeGppLocationBox,
        )
    }

    private fun moovWithUdtaChildren(vararg children: ByteArray): ByteArray =
        box("moov", box("udta", children.fold(ByteArray(0)) { acc, part -> acc + part }))

    private fun box(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        require(typeBytes.size == 4)
        val size = 8 + payload.size
        val header = byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + typeBytes
        return header + payload
    }
}
