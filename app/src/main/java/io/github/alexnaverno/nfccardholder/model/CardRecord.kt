package io.github.alexnaverno.nfccardholder.model

/**
 * One AID the phone should answer for, together with the response that must be replayed when a
 * reader selects it.
 *
 * @param aid upper case hex, 5..16 bytes
 * @param response full response APDU including the status word, upper case hex. An empty string
 * means "answer `9000` with no data".
 * @param source where the entry came from: [SOURCE_PROBE], [SOURCE_MANUAL] or [SOURCE_IMPORTED].
 */
class AidEntry(
    var aid: String = "",
    var response: String = "",
    var source: String = SOURCE_PROBE
) {
    companion object {
        const val SOURCE_PROBE = "probe"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IMPORTED = "import"
    }
}

/**
 * A recorded command/response pair. It is replayed verbatim before any other matching rule, which
 * makes it possible to store a short APDU script captured from a real card.
 */
class ApduPair(var request: String = "", var response: String = "")

/** One MIFARE Classic sector: the keys that unlocked it and the blocks that were readable. */
class SectorDump(
    var index: Int = 0,
    var keyA: String = "",
    var keyB: String = "",
    var blocks: MutableList<String> = mutableListOf()
)

/**
 * Everything the app knows about one copied card.
 *
 * Plain Kotlin only: (de)serialisation lives in [io.github.alexnaverno.nfccardholder.model.CardJson]
 * so that this class stays usable from JVM unit tests.
 */
class CardRecord {

    /** User visible name of the slot. */
    var name: String = ""

    /** Tag UID as upper case hex. */
    var uid: String = ""

    /** Short technology names, e.g. `NfcA`, `IsoDep`, `MifareClassic`. */
    var techs: List<String> = emptyList()

    /** ATQA/SENS_RES as hex (NFC-A). */
    var atqa: String = ""

    /** SAK/SEL_RES as hex (NFC-A). */
    var sak: String = ""

    /** True when the tag speaks ISO-DEP (ISO 14443-4), the only protocol HCE can emulate. */
    var isoDep: Boolean = false

    /** ATS historical bytes as hex. */
    var historicalBytes: String = ""

    /** NFC-B higher layer response as hex. */
    var hiLayerResponse: String = ""

    /** Raw NDEF message as hex, empty when the tag carries no NDEF. */
    var ndefHex: String = ""

    /** Human readable summary of the NDEF payload (text records, URIs). */
    var ndefText: String = ""

    /** MIFARE flavour, e.g. `Classic`, `Ultralight C`. */
    var mifareType: String = ""

    /** MIFARE Classic size in bytes. */
    var mifareSize: Int = 0

    /** Number of MIFARE Classic sectors that could be authenticated. */
    var sectorsRead: Int = 0

    /** Total number of MIFARE Classic sectors. */
    var sectorsTotal: Int = 0

    /** Sector contents keyed with the keys that unlocked them. */
    val sectors: MutableList<SectorDump> = mutableListOf()

    /** MIFARE Ultralight page count. */
    var ultralightPages: Int = 0

    /** MIFARE Ultralight memory dump as hex. */
    var ultralightHex: String = ""

    /** Epoch millis of the copy operation. */
    var createdAt: Long = 0L

    /** How many times this card answered a reader while being emulated. */
    var taps: Int = 0

    /** AIDs to answer for while this card is active. */
    val aids: MutableList<AidEntry> = mutableListOf()

    /** Recorded command/response pairs, matched before any generated answer. */
    val pairs: MutableList<ApduPair> = mutableListOf()

    /** Free form log lines produced while copying. */
    val notes: MutableList<String> = mutableListOf()

    fun isPresent(): Boolean = uid.isNotEmpty() || techs.isNotEmpty() || aids.isNotEmpty()

    /**
     * How the card can be transmitted by the phone.
     *
     *  * [MODE_AID] - at least one AID with a recorded response is known.
     *  * [MODE_TYPE4] - no AID is known but the card carries NDEF, so it can be served as an
     *    NFC Forum Type 4 Tag (AID `D2760000850101`).
     *  * [MODE_NONE] - the phone cannot transmit anything meaningful for this card.
     */
    fun emulationMode(): String = when {
        aids.any { it.aid.isNotEmpty() } -> MODE_AID
        ndefHex.isNotEmpty() -> MODE_TYPE4
        else -> MODE_NONE
    }

    fun canEmulate(): Boolean = emulationMode() != MODE_NONE

    /** Adds or replaces an AID entry. Returns false when [aid] is blank. */
    fun putAid(aid: String, response: String, source: String): Boolean {
        val normalized = aid.trim().uppercase()
        if (normalized.isEmpty()) return false
        val existing = aids.indexOfFirst { it.aid.equals(normalized, ignoreCase = true) }
        val entry = AidEntry(normalized, response.trim().uppercase(), source)
        return if (existing >= 0) {
            aids[existing] = entry
            true
        } else {
            aids.add(entry)
            true
        }
    }

    /** Looks up the entry registered for [aid] (exact match, case insensitive). */
    fun findAid(aid: String): AidEntry? {
        val wanted = aid.trim().uppercase()
        return aids.firstOrNull { it.aid.equals(wanted, ignoreCase = true) }
    }

    companion object {
        const val MODE_AID = "aid"
        const val MODE_TYPE4 = "type4"
        const val MODE_NONE = "none"
    }
}
