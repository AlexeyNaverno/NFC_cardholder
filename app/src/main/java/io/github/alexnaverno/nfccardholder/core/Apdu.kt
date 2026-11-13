package io.github.alexnaverno.nfccardholder.core

/**
 * A parsed ISO 7816-4 command APDU.
 *
 * Only short length fields (single byte Lc/Le) are supported: that is all an NFC reader can send
 * over host card emulation.
 */
class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray,
    /** Expected response length. `0` never happens: `Le = 0x00` is reported as 256. */
    val le: Int,
    val hasLe: Boolean,
    val raw: ByteArray
) {
    val isSelect: Boolean get() = ins == INS_SELECT
    val isSelectByName: Boolean get() = isSelect && p1 == 0x04
    val isSelectByFileId: Boolean get() = isSelect && p1 == 0x00
    val isReadBinary: Boolean get() = ins == INS_READ_BINARY
    val isUpdateBinary: Boolean get() = ins == INS_UPDATE_BINARY

    /** The selected DF/application name as an upper case hex string, without a trailing `00` Le. */
    fun nameHex(): String = Hex.encode(Apdu.stripTrailingZero(data))

    /** The selected file identifier as an upper case hex string. */
    fun fileIdHex(): String = Hex.encode(data)

    /** Offset for READ/UPDATE BINARY, encoded in P1:P2. */
    fun offset(): Int = (p1 shl 8) or p2

    override fun toString(): String = Hex.encodeSpaced(raw)

    companion object {
        const val INS_SELECT = 0xA4
        const val INS_READ_BINARY = 0xB0
        const val INS_UPDATE_BINARY = 0xD6
        const val INS_GET_DATA = 0xCA

        /**
         * Parses [apdu] into the four header/case variants of ISO 7816-4.
         *
         * @return null when the APDU is too short, malformed or uses extended length fields.
         */
        fun parse(apdu: ByteArray?): CommandApdu? {
            if (apdu == null || apdu.size < HEADER_SIZE) return null
            val cla = apdu[0].toInt() and 0xFF
            val ins = apdu[1].toInt() and 0xFF
            val p1 = apdu[2].toInt() and 0xFF
            val p2 = apdu[3].toInt() and 0xFF

            // Case 1: header only.
            if (apdu.size == HEADER_SIZE) {
                return CommandApdu(cla, ins, p1, p2, ByteArray(0), 0, false, apdu)
            }
            // Case 2: header + Le.
            if (apdu.size == HEADER_SIZE + 1) {
                val le = apdu[4].toInt() and 0xFF
                return CommandApdu(cla, ins, p1, p2, ByteArray(0), if (le == 0) 256 else le, true, apdu)
            }
            val lc = apdu[4].toInt() and 0xFF
            if (lc == 0 || apdu.size < HEADER_SIZE + 1 + lc) return null
            val data = apdu.copyOfRange(HEADER_SIZE + 1, HEADER_SIZE + 1 + lc)
            return when (apdu.size - (HEADER_SIZE + 1 + lc)) {
                // Case 3: header + Lc + data.
                0 -> CommandApdu(cla, ins, p1, p2, data, 0, false, apdu)
                // Case 4: header + Lc + data + Le.
                1 -> {
                    val le = apdu[apdu.size - 1].toInt() and 0xFF
                    CommandApdu(cla, ins, p1, p2, data, if (le == 0) 256 else le, true, apdu)
                }
                // Extended length or garbage: not supported over HCE.
                else -> null
            }
        }

        private const val HEADER_SIZE = 4
    }
}

/**
 * APDU construction helpers and status words.
 *
 * Pure Kotlin (no Android imports) so that it can be covered by JVM unit tests.
 */
object Apdu {

    /** `9000` - normal processing. */
    val SW_OK: ByteArray = byteArrayOf(0x90.toByte(), 0x00)

    /** `6700` - wrong length. */
    val SW_WRONG_LENGTH: ByteArray = byteArrayOf(0x67, 0x00)

    /** `6982` - security status not satisfied. */
    val SW_SECURITY: ByteArray = byteArrayOf(0x69.toByte(), 0x82.toByte())

    /** `6A82` - file or application not found. */
    val SW_FILE_NOT_FOUND: ByteArray = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    /** `6A86` - incorrect parameters P1-P2. */
    val SW_WRONG_PARAMETERS: ByteArray = byteArrayOf(0x6A.toByte(), 0x86.toByte())

    /** `6B00` - wrong offset. */
    val SW_WRONG_OFFSET: ByteArray = byteArrayOf(0x6B, 0x00)

    /** `6D00` - instruction not supported. */
    val SW_INS_NOT_SUPPORTED: ByteArray = byteArrayOf(0x6D, 0x00)

    /** `6E00` - class not supported. */
    val SW_CLA_NOT_SUPPORTED: ByteArray = byteArrayOf(0x6E, 0x00)

    const val SW_VALUE_OK = 0x9000

    private const val TAG_APPLICATION_LABEL = 0x4F

    /** Constructed bit of a BER-TLV tag: the value holds nested TLV objects. */
    private const val TAG_CONSTRUCTED = 0x20

    /** Builds `00 A4 04 00 Lc <name> 00` (SELECT by DF name, first or only occurrence). */
    fun selectByName(name: ByteArray): ByteArray {
        val apdu = ByteArray(6 + name.size)
        apdu[0] = 0x00
        apdu[1] = CommandApdu.INS_SELECT.toByte()
        apdu[2] = 0x04
        apdu[3] = 0x00
        apdu[4] = name.size.toByte()
        System.arraycopy(name, 0, apdu, 5, name.size)
        apdu[apdu.size - 1] = 0x00
        return apdu
    }

    /**
     * Builds `00 A4 00 0C 02 <idHi> <idLo>` (SELECT by file identifier, no response data
     * requested). This is a case 3 APDU, so it carries no Le byte.
     */
    fun selectByFileId(fileId: ByteArray): ByteArray {
        val apdu = ByteArray(HEADER_SIZE + 1 + fileId.size)
        apdu[0] = 0x00
        apdu[1] = CommandApdu.INS_SELECT.toByte()
        apdu[2] = 0x00
        apdu[3] = 0x0C
        apdu[4] = fileId.size.toByte()
        System.arraycopy(fileId, 0, apdu, HEADER_SIZE + 1, fileId.size)
        return apdu
    }

    private const val HEADER_SIZE = 4

    /** Builds `00 B0 <p1> <p2> <le>` (READ BINARY). */
    fun readBinary(offset: Int, le: Int): ByteArray = byteArrayOf(
        0x00,
        CommandApdu.INS_READ_BINARY.toByte(),
        ((offset shr 8) and 0xFF).toByte(),
        (offset and 0xFF).toByte(),
        (le and 0xFF).toByte()
    )

    /** The status word of a response, or -1 when the response is missing or truncated. */
    fun statusWord(response: ByteArray?): Int {
        if (response == null || response.size < 2) return -1
        val high = response[response.size - 2].toInt() and 0xFF
        val low = response[response.size - 1].toInt() and 0xFF
        return (high shl 8) or low
    }

    /** True when the response ends with `9000`. */
    fun isOk(response: ByteArray?): Boolean = statusWord(response) == SW_VALUE_OK

    /** Drops a single trailing `00` (an Le byte that a reader appended to the DF name). */
    fun stripTrailingZero(data: ByteArray): ByteArray {
        if (data.size > 1 && data[data.size - 1] == 0x00.toByte()) {
            return data.copyOfRange(0, data.size - 1)
        }
        return data
    }

    /**
     * Returns every value of BER-TLV tag `4F` (application identifier) found in [data],
     * recursing into constructed tags such as the FCI template `6F`/`A5`/`BF0C`/`61`.
     *
     * Values that are not 5..16 bytes long are ignored because they cannot be AIDs.
     */
    fun extractAids(data: ByteArray?): List<String> {
        val found = mutableListOf<ByteArray>()
        collectTag(data ?: return emptyList(), TAG_APPLICATION_LABEL, found, 0)
        val result = mutableListOf<String>()
        for (value in found) {
            if (value.size in MIN_AID_BYTES..MAX_AID_BYTES) {
                val hex = Hex.encode(value)
                if (hex !in result) result.add(hex)
            }
        }
        return result
    }

    const val MIN_AID_BYTES = 5
    const val MAX_AID_BYTES = 16

    /** True when [hex] can be registered with Android card emulation (5..16 bytes of hex). */
    fun isValidAid(hex: String?): Boolean {
        if (hex == null) return false
        if (hex.length % 2 != 0) return false
        val bytes = hex.length / 2
        if (bytes !in MIN_AID_BYTES..MAX_AID_BYTES) return false
        return Hex.isValid(hex)
    }

    private fun collectTag(data: ByteArray, tag: Int, out: MutableList<ByteArray>, depth: Int) {
        if (depth > MAX_TLV_DEPTH) return
        var i = 0
        while (i < data.size) {
            val first = data[i].toInt() and 0xFF
            i++
            // Padding / free bytes.
            if (first == 0x00 || first == 0xFF) continue
            var current = first
            // The constructed bit always lives in the first tag byte, also for two byte tags.
            val constructed = (first and TAG_CONSTRUCTED) != 0
            // Two byte tags: the low five bits of the first byte are all ones.
            if ((first and 0x1F) == 0x1F) {
                if (i >= data.size) return
                current = (first shl 8) or (data[i].toInt() and 0xFF)
                i++
            }
            if (i >= data.size) return
            var length = data[i].toInt() and 0xFF
            i++
            if (length and 0x80 != 0) {
                val lengthBytes = length and 0x7F
                if (lengthBytes == 0 || lengthBytes > 2 || i + lengthBytes > data.size) return
                length = 0
                for (k in 0 until lengthBytes) {
                    length = (length shl 8) or (data[i + k].toInt() and 0xFF)
                }
                i += lengthBytes
            }
            if (length < 0 || i + length > data.size) return
            val value = data.copyOfRange(i, i + length)
            if (current == tag) out.add(value)
            // Constructed tag: recurse into its content.
            if (constructed) collectTag(value, tag, out, depth + 1)
            i += length
        }
    }

    private const val MAX_TLV_DEPTH = 8
}
