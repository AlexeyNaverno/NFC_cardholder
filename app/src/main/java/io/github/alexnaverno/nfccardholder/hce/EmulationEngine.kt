package io.github.alexnaverno.nfccardholder.hce

import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.core.CommandApdu
import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.model.AidEntry
import io.github.alexnaverno.nfccardholder.model.CardRecord

/**
 * Decides what the phone answers when an NFC reader talks to the copied card.
 *
 * Answering order:
 *  1. an exactly recorded command/response pair (verbatim replay),
 *  2. SELECT by AID that the card answered with,
 *  3. NFC Forum Type 4 Tag file handling when NDEF data was copied,
 *  4. a standard error status word.
 *
 * Pure Kotlin, covered by JVM unit tests.
 */
class EmulationEngine(card: CardRecord?) {

    private val record: CardRecord? = card

    private val type4: Type4TagEmulator? = createType4(card)

    /** AID selected during the current tap, empty until the reader selects an application. */
    var selectedAid: String = ""
        private set

    /** APDUs answered during the current tap. */
    var exchanges: Int = 0
        private set

    /** Reader taps seen by this engine (one tap = one successful SELECT by AID). */
    var taps: Int = 0
        private set

    /** Called when the RF link is lost or another service takes over. */
    fun reset() {
        selectedAid = ""
        exchanges = 0
        type4?.reset()
    }

    fun respond(apdu: ByteArray): ByteArray {
        val card = record ?: return Apdu.SW_FILE_NOT_FOUND
        exchanges++

        val command = CommandApdu.parse(apdu) ?: return Apdu.SW_WRONG_LENGTH
        val rawHex = Hex.encode(apdu)

        // 1. Verbatim replay of a recorded exchange.
        replay(card, rawHex)?.let { return it }

        // 2. SELECT by AID.
        if (command.isSelectByName) {
            val name = command.nameHex()
            val freshTap = selectedAid.isEmpty()
            val entry = matchAid(card, name)
            if (entry != null) {
                selectedAid = name
                if (freshTap) taps++
                return decodeOrOk(entry.response)
            }
            if (type4 != null && Type4TagEmulator.isType4Aid(name)) {
                selectedAid = name
                if (freshTap) taps++
                return type4.respond(command)
            }
            return Apdu.SW_FILE_NOT_FOUND
        }

        // 3. Type 4 Tag file operations after the NDEF application was selected.
        val tag4 = type4
        if (tag4 != null && Type4TagEmulator.isType4Aid(selectedAid)) {
            return tag4.respond(command)
        }

        // 4. Nothing recorded for this command.
        return if (command.cla == 0x00) Apdu.SW_INS_NOT_SUPPORTED else Apdu.SW_CLA_NOT_SUPPORTED
    }

    private fun replay(card: CardRecord, rawHex: String): ByteArray? {
        for (pair in card.pairs) {
            if (pair.request.isEmpty()) continue
            if (pair.request.equals(rawHex, ignoreCase = true)) return decodeOrOk(pair.response)
        }
        // Readers vary the expected length byte, so retry without the trailing Le.
        if (rawHex.length > MIN_APDU_HEX_LENGTH) {
            val withoutLe = rawHex.substring(0, rawHex.length - 2)
            for (pair in card.pairs) {
                if (pair.request.isEmpty()) continue
                if (pair.request.equals(withoutLe, ignoreCase = true)) {
                    return decodeOrOk(pair.response)
                }
            }
        }
        return null
    }

    /**
     * Finds the entry registered for [name]: exact match first, otherwise the longest AID that is
     * a prefix of the requested name or the other way round.
     */
    private fun matchAid(card: CardRecord, name: String): AidEntry? {
        card.findAid(name)?.let { return it }
        var best: AidEntry? = null
        for (entry in card.aids) {
            val aid = entry.aid
            if (aid.isEmpty() || !Hex.isValid(aid)) continue
            if (name.startsWith(aid, ignoreCase = true) || aid.startsWith(name, ignoreCase = true)) {
                val current = best
                if (current == null || aid.length > current.aid.length) best = entry
            }
        }
        return best
    }

    private fun decodeOrOk(response: String): ByteArray {
        if (response.isBlank()) return Apdu.SW_OK
        return try {
            val decoded = Hex.decode(response)
            if (decoded.isEmpty()) Apdu.SW_OK else decoded
        } catch (e: IllegalArgumentException) {
            Apdu.SW_OK
        }
    }

    private fun createType4(card: CardRecord?): Type4TagEmulator? {
        val ndefHex = card?.ndefHex ?: return null
        if (ndefHex.isBlank()) return null
        val ndef = try {
            Hex.decode(ndefHex)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return if (ndef.isEmpty()) null else Type4TagEmulator(ndef)
    }

    companion object {
        /** Shortest hex string that can still carry a trailing Le byte: `00A4040000`. */
        private const val MIN_APDU_HEX_LENGTH = 10
    }
}
