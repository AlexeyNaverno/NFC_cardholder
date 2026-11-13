package io.github.alexnaverno.nfccardholder.nfc

import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.model.CardStore

/**
 * Keys tried when a MIFARE Classic card is copied.
 *
 * The list is intentionally short: every failed authentication costs an RF round trip (and often a
 * reconnection), so a long dictionary makes copying painfully slow. Keys that already unlocked a
 * sector of another stored card are tried first, which is how a user can build up a working
 * dictionary over time.
 */
object MifareKeys {

    /** Transport and well known application keys, most likely first. */
    val KNOWN: List<String> = listOf(
        "FFFFFFFFFFFF", // transport key
        "000000000000", // blank / zeroed
        "A0A1A2A3A4A5", // MAD (MIFARE Application Directory)
        "D3F7D3F7D3F7", // NDEF application
        "B0B1B2B3B4B5",
        "4D3A99C351DD",
        "1A982C7E459A",
        "AABBCCDDEEFF",
        "714C5C886E97",
        "587EE5F9350F",
        "A0478CC39091",
        "533CB6C723F6",
        "8FD0A4F256E9"
    )

    const val MAX_KEYS = 20

    /** Keys of every stored card, most recently used first. */
    fun learnedKeys(store: CardStore?): List<String> {
        if (store == null) return emptyList()
        val out = LinkedHashSet<String>()
        // Walk the slots backwards so that the newest copies are preferred.
        for (index in store.slots.indices.reversed()) {
            val record = store.slots[index] ?: continue
            record.sectors.forEach { sector ->
                addKey(out, sector.keyA)
                addKey(out, sector.keyB)
            }
        }
        return out.toList()
    }

    /**
     * Builds the working dictionary: learned keys first, then the well known ones, deduplicated and
     * truncated to [MAX_KEYS] entries.
     */
    fun dictionary(store: CardStore?): List<ByteArray> {
        val ordered = LinkedHashSet<String>()
        learnedKeys(store).forEach { addKey(ordered, it) }
        KNOWN.forEach { addKey(ordered, it) }
        return ordered.take(MAX_KEYS).map { Hex.decode(it) }
    }

    private fun addKey(target: MutableSet<String>, key: String?) {
        if (key == null) return
        val normalized = key.trim().uppercase()
        if (normalized.length != KEY_HEX_LENGTH) return
        if (!Hex.isValid(normalized)) return
        target.add(normalized)
    }

    private const val KEY_HEX_LENGTH = 12
}
