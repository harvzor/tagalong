package dev.tagalong.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class Mp4LocationMetadataTest {

    @Test
    fun canonicalSamplesExposeTheExactQuickTimePayload() {
        val expected = mapOf(
            "google-pixel-10a.mp4" to "\u0000\u0011\u0015\u00c7+52.5562+13.3418/",
            // The leading zero in the longitude is part of the source payload and must not
            // be normalized to +13.4064 by a logical metadata round trip.
            "xiaomi-poco-x5.mp4" to "\u0000\u0012\u0015\u00c7+52.5182+013.4064/",
        )

        expected.forEach { (name, expectedPayloadText) ->
            val info = Mp4LocationMetadata.inspect(TestFixtures.sourceFile(TestFixtures.SampleVideo(name)))
            assertTrue("$name must contain moov/udta/©xyz", info.hasQuickTime)
            assertEquals("$name must not need generic metadata to identify its source location", emptySet<String>(), info.genericMdtaKeys)
            assertEquals(1, info.quickTimeLocations.size)
            assertArrayEquals(
                "$name QuickTime payload must be read byte-for-byte",
                expectedPayloadText.toByteArray(StandardCharsets.ISO_8859_1),
                requireNotNull(info.quickTimePayload),
            )
        }
    }

    @Test
    fun walkerRecognizesExtendedSizeBoxesAndGenericMdtaLocationKeys() {
        val locationPayload = byteArrayOf(0, 0x11, 0x15, 0xc7.toByte()) + "+1.0000+2.0000/".toByteArray()
        val xyz = extendedBox("©xyz", locationPayload)
        val locationKey = mdtaKey("location")
        val keys = box("keys", byteArrayOf(0, 0, 0, 0) + int32(1) + locationKey)
        val meta = extendedBox("meta", byteArrayOf(0, 0, 0, 0) + keys)
        val udta = extendedBox("udta", xyz + meta)
        val moov = box("moov", udta)

        val info = Mp4LocationMetadata.inspect(moov)

        assertEquals(LocationRepresentation.BOTH, info.representation)
        assertArrayEquals(locationPayload, requireNotNull(info.quickTimePayload))
        assertEquals(setOf("location"), info.genericMdtaKeys)
    }

    private fun mdtaKey(key: String): ByteArray {
        val keyBytes = "mdta$key".toByteArray(StandardCharsets.ISO_8859_1)
        return int32(4 + keyBytes.size) + keyBytes
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        require(typeBytes.size == 4)
        return int32(8 + payload.size) + typeBytes + payload
    }

    private fun extendedBox(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        require(typeBytes.size == 4)
        return int32(1) + typeBytes + int64(16L + payload.size) + payload
    }

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun int64(value: Long): ByteArray = ByteArray(8) { index ->
        (value ushr ((7 - index) * 8)).toByte()
    }
}
