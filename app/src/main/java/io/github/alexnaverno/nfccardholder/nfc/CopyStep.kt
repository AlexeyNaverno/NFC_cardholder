package io.github.alexnaverno.nfccardholder.nfc

/** Phases of a copy operation, reported to the UI while a card is being read. */
enum class CopyStep {
    /** Technologies, UID and other data that is available without any RF activity. */
    START,

    /** Reading an NDEF message. */
    NDEF,

    /** ISO-DEP layer information. */
    ISO_DEP,

    /** Probing applications with SELECT, the second value is the number of probes so far. */
    PROBE,

    /** Reading MIFARE Ultralight pages, the second value is the page index. */
    ULTRALIGHT,

    /** Unlocking MIFARE Classic sectors, the second value is the sector number. */
    CLASSIC,

    /** Everything that could be read is in the record. */
    DONE
}
