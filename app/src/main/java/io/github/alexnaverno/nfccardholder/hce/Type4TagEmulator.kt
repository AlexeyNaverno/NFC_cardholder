package io.github.alexnaverno.nfccardholder.hce

import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.core.CommandApdu
import io.github.alexnaverno.nfccardholder.core.Hex

/**
 * Emulates an NFC Forum Type 4 Tag that serves a stored NDEF message.
 *
 * This is the only way the phone can hand out NDEF data it copied from a tag that does not speak
 * ISO-DEP itself (for example an NTAG): the reader talks ISO-DEP to the phone, the phone answers
 * as a Type 4 Tag with a synthetic capability container (`E103`) and NDEF file (`E104`).
 *
 * The emulated tag is read only.
 *
 * Pure Kotlin, covered by JVM unit tests.
 */
class Type4TagEmulator(ndefMessage: ByteArray) {

    /** Content of file `E104`: two byte length prefix followed by the NDEF message. */
    private val ndefFile: ByteArray

    /** Content of file `E103`, the capability container. */
    private val capabilityContainer: ByteArray

    private var selectedFile: Int = FILE_NONE

    init {
        val length = ndefMessage.size
        val payload = ByteArray(NDEF_LENGTH_PREFIX + length)
        payload[0] = ((length shr 8) and 0xFF).toByte()
        payload[1] = (length and 0xFF).toByte()
        System.arraycopy(ndefMessage, 0, payload, NDEF_LENGTH_PREFIX, length)
        ndefFile = payload
        capabilityContainer = buildCapabilityContainer(payload.size)
    }

    fun ndefFileBytes(): ByteArray = ndefFile.copyOf()

    fun capabilityContainerBytes(): ByteArray = capabilityContainer.copyOf()

    /** Called when the RF link is lost or another application is selected. */
    fun reset() {
        selectedFile = FILE_NONE
    }

    fun respond(command: CommandApdu): ByteArray {
        if (command.isSelectByName) {
            return if (isType4Aid(command.nameHex())) {
                selectedFile = FILE_NONE
                FCI_RESPONSE
            } else {
                Apdu.SW_FILE_NOT_FOUND
            }
        }
        if (command.isSelectByFileId) {
            val fileId = command.fileIdHex()
            return when {
                fileId.equals(FILE_ID_CC, true) -> {
                    selectedFile = FILE_CC
                    Apdu.SW_OK
                }
                fileId.equals(FILE_ID_NDEF, true) -> {
                    selectedFile = FILE_NDEF
                    Apdu.SW_OK
                }
                else -> Apdu.SW_FILE_NOT_FOUND
            }
        }
        if (command.isReadBinary) {
            val file = when (selectedFile) {
                FILE_CC -> capabilityContainer
                FILE_NDEF -> ndefFile
                else -> return Apdu.SW_FILE_NOT_FOUND
            }
            val offset = command.offset()
            if (offset >= file.size) return Apdu.SW_WRONG_OFFSET
            val wanted = if (command.hasLe) command.le else file.size - offset
            val end = minOf(file.size, offset + wanted)
            return file.copyOfRange(offset, end) + Apdu.SW_OK
        }
        if (command.isUpdateBinary) {
            // The emulated tag is read only.
            return Apdu.SW_SECURITY
        }
        return Apdu.SW_INS_NOT_SUPPORTED
    }

    private fun buildCapabilityContainer(ndefFileSize: Int): ByteArray {
        val advertised = ndefFileSize.coerceIn(0, MAX_NDEF_FILE_SIZE)
        return byteArrayOf(
            0x00, CC_LENGTH,            // capability container length, including these two bytes
            MAPPING_VERSION,            // mapping version 2.0
            0x00, MAX_READ_LENGTH,      // MLe: maximum data length that can be read
            0x00, 0x00,                 // MLC: 0 - writing is not supported
            TLV_NDEF_FILE_CONTROL,      // NDEF File Control TLV
            TLV_NDEF_FILE_LENGTH,
            FILE_ID_NDEF_BYTES[0],
            FILE_ID_NDEF_BYTES[1],
            ((advertised shr 8) and 0xFF).toByte(),
            (advertised and 0xFF).toByte(),
            ACCESS_NO_SECURITY,         // read access: none required
            ACCESS_NEVER                // write access: never
        )
    }

    companion object {
        const val FILE_ID_CC = "E103"
        const val FILE_ID_NDEF = "E104"

        /** True when [aid] addresses the NFC Forum Type 4 Tag application. */
        fun isType4Aid(aid: String?): Boolean =
            aid != null && aid.equals(EmulationAids.TYPE4_NDEF, ignoreCase = true)

        /** Human readable description of the synthetic files, used by the details dialog. */
        fun describe(ndefHex: String): String {
            val ndef = try {
                Hex.decode(ndefHex)
            } catch (e: IllegalArgumentException) {
                return ""
            }
            if (ndef.isEmpty()) return ""
            val emulator = Type4TagEmulator(ndef)
            return FILE_ID_CC + "=" + Hex.encode(emulator.capabilityContainerBytes()) +
                ", " + FILE_ID_NDEF + "=" + Hex.encode(emulator.ndefFileBytes())
        }

        private val FILE_ID_NDEF_BYTES = byteArrayOf(0xE1.toByte(), 0x04)

        /**
         * FCI template returned for SELECT by AID:
         * `6F 0B A5 09 50 07 D2 76 00 00 85 01 01` + `9000`.
         */
        private val FCI_RESPONSE: ByteArray = byteArrayOf(
            0x6F, 0x0B,
            0xA5.toByte(), 0x09,
            0x50, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00,
            0x85.toByte(), 0x01, 0x01,
            0x90.toByte(), 0x00
        )

        private const val NDEF_LENGTH_PREFIX = 2
        private val CC_LENGTH: Byte = 0x0F
        private val MAPPING_VERSION: Byte = 0x20
        private val MAX_READ_LENGTH: Byte = 0xFF.toByte()
        private val TLV_NDEF_FILE_CONTROL: Byte = 0x04
        private val TLV_NDEF_FILE_LENGTH: Byte = 0x06
        private val ACCESS_NO_SECURITY: Byte = 0x00
        private val ACCESS_NEVER: Byte = 0xFF.toByte()
        private const val MAX_NDEF_FILE_SIZE = 0xFFFE

        private const val FILE_NONE = 0
        private const val FILE_CC = 1
        private const val FILE_NDEF = 2
    }
}
