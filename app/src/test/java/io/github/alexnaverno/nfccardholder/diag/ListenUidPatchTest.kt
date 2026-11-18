package io.github.alexnaverno.nfccardholder.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenUidPatchTest {

    @Test
    fun parsesFourAndSevenByteUidsOnly() {
        assertTrue(ListenUidPatch.parse("0D010890")!!.size == 4)
        assertTrue(ListenUidPatch.parse("04010203040506")!!.size == 7)
        assertNull(ListenUidPatch.parse("0D0108"))
        assertNull(ListenUidPatch.parse("ZZ010890"))
        assertNull(ListenUidPatch.parse(null))
    }

    @Test
    fun scriptCarriesTheListenUidTlv() {
        val script = ListenUidPatch.script("0D010890", "Карта 1")
        assertTrue(script.contains("33, 04, 0D, 01, 08, 90"))
        assertTrue(script.contains("sed -i -E"))
        assertTrue(script.contains("svc nfc disable"))
        assertTrue(script.contains("adb root"))
        assertTrue(script.contains("/vendor/etc/libnfc-nxp.conf"))
        assertTrue(script.contains("# Slot: Карта 1, UID 0D010890"))
    }

    @Test
    fun scriptUsesLengthSevenForLongUids() {
        val script = ListenUidPatch.script("04010203040506", "long")
        assertTrue(script.contains("33, 07, 04, 01, 02, 03, 04, 05, 06"))
        assertFalse(script.contains("33, 04,"))
    }

    @Test
    fun invalidUidProducesNoCommands() {
        val script = ListenUidPatch.script("nope", "slot")
        assertFalse(script.contains("sed -i"))
        assertFalse(script.contains("adb root"))
        assertTrue(script.contains("ERROR"))
    }

    @Test
    fun labelNewlinesAreFlattened() {
        val script = ListenUidPatch.script("0D010890", "a\nb")
        assertTrue(script.contains("# Slot: a b, UID 0D010890"))
    }
}
