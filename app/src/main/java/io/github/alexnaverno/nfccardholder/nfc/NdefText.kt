package io.github.alexnaverno.nfccardholder.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import io.github.alexnaverno.nfccardholder.core.Hex

/** Turns an [NdefMessage] into a short human readable summary for the slot list. */
object NdefText {

    /** NFC Forum URI record identifier codes. */
    private val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://",
        "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:",
        "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://",
        "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
        "urn:nfc:"
    )

    fun summarize(message: NdefMessage?): String {
        if (message == null) return ""
        val parts = ArrayList<String>(message.records.size)
        message.records.forEach { record -> parts.add(describe(record)) }
        return parts.filter { it.isNotEmpty() }.joinToString(" | ")
    }

    private fun describe(record: NdefRecord): String {
        val wellKnownText = record.tnf == NdefRecord.TNF_WELL_KNOWN &&
            record.type?.contentEquals(NdefRecord.RTD_TEXT) == true
        if (wellKnownText) return decodeText(record.payload)

        val wellKnownUri = record.tnf == NdefRecord.TNF_WELL_KNOWN &&
            record.type?.contentEquals(NdefRecord.RTD_URI) == true
        if (wellKnownUri) return decodeUri(record.payload)

        return when (record.tnf.toInt()) {
            NdefRecord.TNF_ABSOLUTE_URI.toInt() -> ascii(record.payload)
            NdefRecord.TNF_MIME_MEDIA.toInt() ->
                "MIME " + ascii(record.type) + ": " + Hex.shorten(Hex.encode(record.payload))
            NdefRecord.TNF_EXTERNAL_TYPE.toInt() ->
                ascii(record.type) + ": " + Hex.shorten(Hex.encode(record.payload))
            NdefRecord.TNF_EMPTY.toInt() -> "(empty record)"
            else ->
                "TNF=" + record.tnf + " " + Hex.shorten(Hex.encode(record.type)) + " " +
                    Hex.shorten(Hex.encode(record.payload))
        }
    }

    private fun decodeText(payload: ByteArray?): String {
        if (payload == null || payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val utf16 = (status and 0x80) != 0
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return Hex.encode(payload)
        val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }

    private fun decodeUri(payload: ByteArray?): String {
        if (payload == null || payload.isEmpty()) return ""
        val code = payload[0].toInt() and 0xFF
        val prefix = if (code < URI_PREFIXES.size) URI_PREFIXES[code] else ""
        return prefix + String(payload, 1, payload.size - 1, Charsets.US_ASCII)
    }

    private fun ascii(bytes: ByteArray?): String =
        if (bytes == null) "" else String(bytes, Charsets.US_ASCII)
}
