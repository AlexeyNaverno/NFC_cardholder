package io.github.alexnaverno.nfccardholder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduTest {

    @Test
    fun parsesCaseOne() {
        val command = CommandApdu.parse(Hex.decode("00A40400"))
        assertEquals(0x00, command!!.cla)
        assertEquals(0xA4, command.ins)
        assertEquals(0x04, command.p1)
        assertEquals(0, command.data.size)
        assertFalse(command.hasLe)
    }

    @Test
    fun parsesCaseTwoWithLe() {
        val command = CommandApdu.parse(Hex.decode("00B00000FF"))
        assertEquals(0xFF, command!!.le)
        assertTrue(command.hasLe)
        assertEquals(256, CommandApdu.parse(Hex.decode("00B0000000"))!!.le)
    }

    @Test
    fun parsesCaseThree() {
        val command = CommandApdu.parse(Hex.decode("00A4000C02E103"))
        assertEquals("E103", command!!.fileIdHex())
        assertTrue(command.isSelectByFileId)
        assertFalse(command.hasLe)
    }

    @Test
    fun parsesCaseFourAndStripsLeFromName() {
        val command = CommandApdu.parse(Hex.decode("00A4040007D276000085010100"))
        assertTrue(command!!.isSelectByName)
        assertEquals("D2760000850101", command.nameHex())
        assertEquals(256, command.le)
    }

    @Test
    fun rejectsTruncatedAndExtendedLength() {
        assertNull(CommandApdu.parse(Hex.decode("00A4")))
        assertNull(CommandApdu.parse(null))
        // Lc says five bytes but only two arrived.
        assertNull(CommandApdu.parse(Hex.decode("00A4040005AABB")))
    }

    @Test
    fun buildsSelectByName() {
        assertEquals(
            "00A4040007D276000085010100",
            Hex.encode(Apdu.selectByName(Hex.decode("D2760000850101")))
        )
    }

    @Test
    fun buildsSelectByFileId() {
        assertEquals("00A4000C02E103", Hex.encode(Apdu.selectByFileId(Hex.decode("E103"))))
    }

    @Test
    fun readsStatusWord() {
        assertEquals(0x9000, Apdu.statusWord(Hex.decode("6F009000")))
        assertEquals(0x6A82, Apdu.statusWord(Hex.decode("6A82")))
        assertEquals(-1, Apdu.statusWord(Hex.decode("6A")))
        assertTrue(Apdu.isOk(Hex.decode("9000")))
    }

    @Test
    fun extractsNestedAidsFromFci() {
        val fci = Hex.decode("6F10A50EBF0C0B61094F07A0000000041010")
        assertEquals(listOf("A0000000041010"), Apdu.extractAids(fci))
    }

    @Test
    fun ignoresTooShortTags() {
        // A 4F value of four bytes cannot be an AID, so it must be skipped.
        val fci = Hex.decode("6F0BA509BF0C0661044F04A0000000")
        assertTrue(Apdu.extractAids(fci).isEmpty())
    }

    @Test
    fun validatesAidLength() {
        // Five to sixteen bytes, that is ten to thirty two hex characters.
        assertFalse(Apdu.isValidAid("A000"))
        assertFalse(Apdu.isValidAid("A0000000"))
        assertTrue(Apdu.isValidAid("A000000000"))
        assertTrue(Apdu.isValidAid("D2760000850101"))
        assertTrue(Apdu.isValidAid("D27600008501010203040506070809"))
        assertFalse(Apdu.isValidAid("D2760000850101020304050607080910"))
        assertFalse(Apdu.isValidAid("ZZ760000850101"))
        assertFalse(Apdu.isValidAid("D276000085010"))
        assertFalse(Apdu.isValidAid(null))
    }
}
