package io.github.alexnaverno.nfccardholder.hce

import io.github.alexnaverno.nfccardholder.core.CommandApdu
import io.github.alexnaverno.nfccardholder.core.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Type4TagEmulatorTest {

    private val ndef = Hex.decode("D101055402656E4869")

    private fun emulatorWithSelectedNdefFile(): Type4TagEmulator {
        val emulator = Type4TagEmulator(ndef)
        val select = CommandApdu.parse(Hex.decode("00A4000C02E104"))
        assertEquals("9000", Hex.encode(emulator.respond(select!!)))
        return emulator
    }

    @Test
    fun capabilityContainerIsFifteenBytes() {
        val emulator = Type4TagEmulator(ndef)
        assertEquals(15, emulator.capabilityContainerBytes().size)
        assertEquals(2 + ndef.size, emulator.ndefFileSize)
    }

    @Test
    fun offsetBeyondTheFileIsRejected() {
        val emulator = emulatorWithSelectedNdefFile()
        val command = CommandApdu.parse(Hex.decode("00B000FF00"))
        assertEquals("6B00", Hex.encode(emulator.respond(command!!)))
    }

    @Test
    fun readingWithoutLeReturnsTheWholeFile() {
        val emulator = emulatorWithSelectedNdefFile()
        // Case 3 READ BINARY has no Le: everything from the offset is returned.
        val command = CommandApdu.parse(Hex.decode("00B00000"))
        val response = emulator.respond(command!!)
        assertTrue(Hex.encode(response).startsWith("0009D101055402656E4869"))
    }

    @Test
    fun unknownFileIdIsNotFound() {
        val emulator = Type4TagEmulator(ndef)
        val command = CommandApdu.parse(Hex.decode("00A4000C02E105"))
        assertEquals("6A82", Hex.encode(emulator.respond(command!!)))
    }
}
