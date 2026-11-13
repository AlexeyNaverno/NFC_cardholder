package io.github.alexnaverno.nfccardholder.hce

import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.model.AidEntry
import io.github.alexnaverno.nfccardholder.model.ApduPair
import io.github.alexnaverno.nfccardholder.model.CardRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class EmulationEngineTest {

    private fun isoDepCard(): CardRecord {
        val card = CardRecord()
        card.isoDep = true
        card.putAid("A0000000041010", "6F0CA50A500800000000000000009000", AidEntry.SOURCE_PROBE)
        card.pairs.add(ApduPair("00B0000004", "010203049000"))
        return card
    }

    @Test
    fun replaysRecordedSelectResponse() {
        val engine = EmulationEngine(isoDepCard())
        val response = engine.respond(Hex.decode("00A4040007A000000004101000"))
        assertEquals("6F0CA50A500800000000000000009000", Hex.encode(response))
        assertEquals("A0000000041010", engine.selectedAid)
    }

    @Test
    fun unknownAidIsNotFound() {
        val engine = EmulationEngine(isoDepCard())
        assertEquals("6A82", Hex.encode(engine.respond(Hex.decode("00A4040007A000000004306000"))))
    }

    @Test
    fun prefixMatchingFindsLongerAid() {
        val engine = EmulationEngine(isoDepCard())
        // The reader selects a five byte prefix of the stored AID.
        val response = engine.respond(Hex.decode("00A4040005A00000000400"))
        assertEquals("6F0CA50A500800000000000000009000", Hex.encode(response))
    }

    @Test
    fun replaysRecordedReadBinary() {
        val engine = EmulationEngine(isoDepCard())
        assertEquals("010203049000", Hex.encode(engine.respond(Hex.decode("00B0000004"))))
    }

    @Test
    fun unsupportedCommandsAnswerWithStandardErrors() {
        val engine = EmulationEngine(isoDepCard())
        assertEquals("6D00", Hex.encode(engine.respond(Hex.decode("00B1000000"))))
        assertEquals("6E00", Hex.encode(engine.respond(Hex.decode("80CA9F7F00"))))
        assertEquals("6700", Hex.encode(engine.respond(Hex.decode("00A4040005AA"))))
    }

    @Test
    fun withoutCardEverythingIsNotFound() {
        val engine = EmulationEngine(null)
        assertEquals("6A82", Hex.encode(engine.respond(Hex.decode("00A4040007A000000004101000"))))
    }

    @Test
    fun countsOneTapPerSelectedApplication() {
        val engine = EmulationEngine(isoDepCard())
        engine.respond(Hex.decode("00A4040007A000000004101000"))
        engine.respond(Hex.decode("00B0000004"))
        engine.respond(Hex.decode("00A4040007A000000004101000"))
        assertEquals(1, engine.taps)
        assertEquals(3, engine.exchanges)
        engine.reset()
        assertEquals(0, engine.taps)
        assertEquals(0, engine.exchanges)
    }

    // ------------------------------------------------------------ Type 4 Tag over HCE

    private fun ndefCard(): CardRecord {
        val card = CardRecord()
        // NDEF text record "Hi" with language code "en".
        card.ndefHex = "D101055402656E4869"
        return card
    }

    @Test
    fun servesNdefAsType4Tag() {
        val engine = EmulationEngine(ndefCard())

        val fci = engine.respond(Hex.decode("00A4040007D276000085010100"))
        assertEquals("6F0BA5095007D27600008501019000", Hex.encode(fci))

        assertEquals("9000", Hex.encode(engine.respond(Hex.decode("00A4000C02E103"))))
        val cc = engine.respond(Hex.decode("00B000000F"))
        assertEquals("000F2000FF00000406E104000B00FF9000", Hex.encode(cc))

        assertEquals("9000", Hex.encode(engine.respond(Hex.decode("00A4000C02E104"))))
        assertEquals("00099000", Hex.encode(engine.respond(Hex.decode("00B0000002"))))
        assertEquals(
            "D101055402656E48699000",
            Hex.encode(engine.respond(Hex.decode("00B0000209")))
        )
    }

    @Test
    fun type4TagIsReadOnly() {
        val engine = EmulationEngine(ndefCard())
        engine.respond(Hex.decode("00A4040007D276000085010100"))
        assertEquals("6982", Hex.encode(engine.respond(Hex.decode("00D60000020102"))))
    }

    @Test
    fun readsBeforeSelectingAFileAreRejected() {
        val engine = EmulationEngine(ndefCard())
        engine.respond(Hex.decode("00A4040007D276000085010100"))
        assertEquals("6A82", Hex.encode(engine.respond(Hex.decode("00B0000002"))))
    }
}
