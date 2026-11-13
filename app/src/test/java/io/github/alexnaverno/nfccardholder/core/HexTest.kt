package io.github.alexnaverno.nfccardholder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexTest {

    @Test
    fun encodeProducesUpperCaseWithoutSeparators() {
        assertEquals("00A4FF90", Hex.encode(byteArrayOf(0x00, 0xA4.toByte(), 0xFF.toByte(), 0x90.toByte())))
        assertEquals("", Hex.encode(null))
        assertEquals("", Hex.encode(ByteArray(0)))
    }

    @Test
    fun encodeSpacedInsertsSingleSpaces() {
        assertEquals("0A 1F", Hex.encodeSpaced(byteArrayOf(0x0A, 0x1F)))
    }

    @Test
    fun decodeIgnoresSeparatorsAndPrefix() {
        assertEquals("0A1F", Hex.encode(Hex.decode("0a:1f")))
        assertEquals("0A1F", Hex.encode(Hex.decode("0x0A 1F")))
        assertEquals("", Hex.encode(Hex.decode(null)))
    }

    @Test
    fun decodeRejectsOddLength() {
        try {
            Hex.decode("ABC")
            throw AssertionError("odd length accepted")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun decodeRejectsNonHexCharacters() {
        assertFalse(Hex.isValid("ZZ"))
        assertFalse(Hex.isValid("A"))
        assertTrue(Hex.isValid("deadbeef"))
        assertTrue(Hex.isValid(""))
    }

    @Test
    fun shortenKeepsHeadAndTail() {
        assertEquals("04A1..F6", Hex.shorten("04A1B2C3D4E5F6"))
        assertEquals("ABCD", Hex.shorten("ABCD", 6, 2))
    }
}
