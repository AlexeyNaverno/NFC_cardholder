package io.github.alexnaverno.nfccardholder.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import android.util.Log
import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.hce.EmulationAids
import io.github.alexnaverno.nfccardholder.model.AidEntry
import io.github.alexnaverno.nfccardholder.model.CardRecord
import io.github.alexnaverno.nfccardholder.model.CardStore
import io.github.alexnaverno.nfccardholder.model.SectorDump
import java.io.IOException

/**
 * Reads everything a tag is willing to hand out and stores it in a [CardRecord].
 *
 * All methods run on a worker thread: NFC I/O blocks and must never be called on the main thread.
 * The [progress] callback is also invoked on that worker thread.
 */
object TagCopier {

    /**
     * @param store used to reuse MIFARE keys that were learned from other slots, may be null.
     */
    fun copy(
        tag: Tag,
        defaultName: String,
        store: CardStore?,
        progress: (CopyStep, Int) -> Unit
    ): CardRecord {
        val record = CardRecord()
        record.name = defaultName
        record.uid = Hex.encode(tag.id)
        record.techs = tag.techList.map { it.substringAfterLast('.') }
        record.createdAt = System.currentTimeMillis()
        record.notes.add("Technologies: " + record.techs.joinToString(", "))

        progress(CopyStep.START, record.techs.size)
        readPassiveInfo(tag, record)

        progress(CopyStep.NDEF, 0)
        readNdef(tag, record)

        progress(CopyStep.ISO_DEP, 0)
        readIsoDep(tag, record, progress)

        progress(CopyStep.ULTRALIGHT, 0)
        readMifareUltralight(tag, record, progress)

        progress(CopyStep.CLASSIC, 0)
        readMifareClassic(tag, record, store, progress)

        progress(CopyStep.DONE, record.aids.size)
        return record
    }

    /** Data that is already known from tag discovery, no RF activity needed. */
    private fun readPassiveInfo(tag: Tag, record: CardRecord) {
        runSafely(record, "NFC-A") {
            NfcA.get(tag)?.let { nfcA ->
                record.atqa = Hex.encode(nfcA.atqa)
                record.sak = "%04X".format(nfcA.sak.toInt() and 0xFFFF)
                record.notes.add("NFC-A ATQA=" + record.atqa + " SAK=" + record.sak)
            }
        }
        runSafely(record, "NFC-B") {
            NfcB.get(tag)?.let { nfcB ->
                record.notes.add("NFC-B application data: " + Hex.encode(nfcB.applicationData))
                record.notes.add("NFC-B protocol info: " + Hex.encode(nfcB.protocolInfo))
            }
        }
        runSafely(record, "NFC-F") {
            NfcF.get(tag)?.let { nfcF ->
                record.notes.add("NFC-F system code: " + Hex.encode(nfcF.systemCode))
                record.notes.add("NFC-F manufacturer: " + Hex.encode(nfcF.manufacturer))
            }
        }
        runSafely(record, "NFC-V") {
            NfcV.get(tag)?.let { nfcV ->
                record.notes.add(
                    "NFC-V DSF ID: " + "%02X".format(nfcV.dsfId.toInt() and 0xFF) +
                        ", response flags: " + "%02X".format(nfcV.responseFlags.toInt() and 0xFF)
                )
            }
        }
    }

    private fun readNdef(tag: Tag, record: CardRecord) {
        val ndef = Ndef.get(tag) ?: return
        var opened = false
        try {
            if (!ndef.isConnected) {
                ndef.connect()
                opened = true
            }
            val message = try {
                ndef.ndefMessage
            } catch (e: IOException) {
                ndef.cachedNdefMessage
            }
            record.notes.add(
                "NDEF: type=" + ndef.type + ", records=" +
                    (message?.records?.size ?: 0) + ", capacity=" + ndef.maxSize + " bytes" +
                    ", writable=" + ndef.isWritable
            )
            if (message != null) {
                record.ndefHex = Hex.encode(message.toByteArray())
                record.ndefText = NdefText.summarize(message)
            } else {
                val cached = ndef.cachedNdefMessage
                if (cached != null) {
                    record.ndefHex = Hex.encode(cached.toByteArray())
                    record.ndefText = NdefText.summarize(cached)
                }
            }
        } catch (e: Exception) {
            note(record, "NDEF", e)
        } finally {
            if (opened) close(ndef)
        }
    }

    private fun readIsoDep(tag: Tag, record: CardRecord, progress: (CopyStep, Int) -> Unit) {
        val isoDep = IsoDep.get(tag) ?: return
        record.isoDep = true
        runSafely(record, "ISO-DEP ATS") {
            record.historicalBytes = Hex.encode(isoDep.historicalBytes)
        }
        runSafely(record, "ISO-DEP ATQB") {
            record.hiLayerResponse = Hex.encode(isoDep.hiLayerResponse)
        }
        if (record.historicalBytes.isNotEmpty()) {
            record.notes.add("ISO-DEP historical bytes: " + record.historicalBytes)
        }
        if (record.hiLayerResponse.isNotEmpty()) {
            record.notes.add("ISO-DEP higher layer response: " + record.hiLayerResponse)
        }
        try {
            isoDep.connect()
            runSafely(record, "ISO-DEP timeout") { isoDep.timeout = ISO_TIMEOUT_MS }
            probeApplications(isoDep, record, progress)
        } catch (e: Exception) {
            note(record, "ISO-DEP", e)
        } finally {
            close(isoDep)
        }
    }

    /**
     * Selects the well known applications and every application identifier they advertise, and
     * remembers the answer of each one so that it can be replayed while the card is transmitted.
     */
    private fun probeApplications(
        isoDep: IsoDep,
        record: CardRecord,
        progress: (CopyStep, Int) -> Unit
    ) {
        val queue = ArrayDeque<String>()
        EmulationAids.PROBE.forEach { queue.addLast(it.uppercase()) }
        val seen = LinkedHashSet<String>()
        var found = 0
        while (queue.isNotEmpty() && found < MAX_AIDS) {
            val aid = queue.removeFirst()
            if (!seen.add(aid)) continue
            progress(CopyStep.PROBE, seen.size)
            val response = try {
                isoDep.transceive(Apdu.selectByName(Hex.decode(aid)))
            } catch (e: IOException) {
                note(record, "ISO-DEP probe", e)
                return
            }
            if (!Apdu.isOk(response)) continue
            found++
            record.putAid(aid, Hex.encode(response), AidEntry.SOURCE_PROBE)
            record.notes.add("Application " + aid + " answered: " + Hex.encode(response))
            for (nested in Apdu.extractAids(response)) {
                val normalized = nested.uppercase()
                if (normalized !in seen) queue.addLast(normalized)
            }
        }
        if (found == 0) {
            record.notes.add(
                "ISO-DEP: no application answered a SELECT, the AID has to be added manually"
            )
        }
    }

    private fun readMifareUltralight(
        tag: Tag,
        record: CardRecord,
        progress: (CopyStep, Int) -> Unit
    ) {
        val ultralight = MifareUltralight.get(tag) ?: return
        record.mifareType =
            if (ultralight.type == MifareUltralight.TYPE_ULTRALIGHT_C) "Ultralight C" else "Ultralight"
        val memory = StringBuilder()
        try {
            ultralight.connect()
            val pages = pageCount(ultralight)
            record.ultralightPages = pages
            var page = 0
            while (page + MifareUltralight.PAGE_SIZE <= pages) {
                progress(CopyStep.ULTRALIGHT, page)
                val data = try {
                    ultralight.readPages(page)
                } catch (e: IOException) {
                    note(record, "Ultralight page $page", e)
                    null
                } ?: break
                memory.append(Hex.encode(data))
                page += MifareUltralight.PAGE_SIZE
            }
        } catch (e: Exception) {
            note(record, "Ultralight", e)
        } finally {
            close(ultralight)
        }
        record.ultralightHex = memory.toString()
        if (record.ultralightHex.isNotEmpty()) {
            record.notes.add(
                "MIFARE " + record.mifareType + ": " + (record.ultralightHex.length / 2) +
                    " bytes of memory read"
            )
        }
    }

    /**
     * `MifareUltralight` has no page count API, so it is derived from the tag type and, for
     * NTAG style tags, from the GET VERSION answer. Reads that go past the end of the memory wrap
     * around, therefore a slightly wrong count only duplicates a few pages at the tail.
     */
    private fun pageCount(ultralight: MifareUltralight): Int = when (ultralight.type) {
        MifareUltralight.TYPE_ULTRALIGHT -> PAGES_ULTRALIGHT
        MifareUltralight.TYPE_ULTRALIGHT_C -> PAGES_ULTRALIGHT_C
        else -> versionPageCount(ultralight)
    }

    private fun versionPageCount(ultralight: MifareUltralight): Int = try {
        val version = ultralight.transceive(GET_VERSION_COMMAND)
        when (version.getOrNull(6)?.toInt()?.and(0xFF)) {
            STORAGE_NTAG213 -> PAGES_NTAG213
            STORAGE_NTAG215 -> PAGES_NTAG215
            STORAGE_NTAG216 -> PAGES_NTAG216
            else -> PAGES_DEFAULT
        }
    } catch (e: IOException) {
        PAGES_DEFAULT
    }

    private fun readMifareClassic(
        tag: Tag,
        record: CardRecord,
        store: CardStore?,
        progress: (CopyStep, Int) -> Unit
    ) {
        val classic = MifareClassic.get(tag) ?: return
        record.mifareType = when (classic.type) {
            MifareClassic.TYPE_CLASSIC -> "Classic"
            MifareClassic.TYPE_PLUS -> "Plus"
            MifareClassic.TYPE_PRO -> "Pro"
            else -> "Unknown"
        }
        record.mifareSize = classic.size
        record.sectorsTotal = classic.sectorCount
        if (record.mifareType.isNotEmpty()) {
            record.notes.add(
                "MIFARE " + record.mifareType + ": " + record.mifareSize + " bytes, " +
                    record.sectorsTotal + " sectors"
            )
        }
        val working = MifareKeys.dictionary(store).map { Hex.encode(it) }.toMutableList()
        val deadline = System.currentTimeMillis() + CLASSIC_TIME_BUDGET_MS
        try {
            classic.connect()
        } catch (e: IOException) {
            note(record, "MIFARE connect", e)
            return
        }
        val locked = ArrayList<Int>()
        try {
            for (sector in 0 until classic.sectorCount) {
                progress(CopyStep.CLASSIC, sector + 1)
                if (System.currentTimeMillis() > deadline) {
                    record.notes.add("MIFARE dump stopped after $CLASSIC_TIME_BUDGET_MS ms")
                    break
                }
                val auth = authenticate(classic, sector, working)
                if (auth == null) {
                    locked.add(sector)
                    continue
                }
                // Neighbouring sectors usually share a key: try the working key first next time.
                working.remove(auth.keyHex)
                working.add(0, auth.keyHex)

                val blocks = readSectorBlocks(classic, sector, auth)
                val dump = SectorDump(
                    index = sector,
                    keyA = if (auth.withKeyB) "" else auth.keyHex,
                    keyB = if (auth.withKeyB) auth.keyHex else ""
                )
                dump.blocks.addAll(blocks)
                rememberTrailerKeyB(dump)
                record.sectors.add(dump)
                record.sectorsRead++
            }
        } catch (e: Exception) {
            note(record, "MIFARE dump", e)
        } finally {
            close(classic)
        }
        record.notes.add(
            "MIFARE sectors read: " + record.sectorsRead + " of " + record.sectorsTotal
        )
        if (locked.isNotEmpty()) {
            record.notes.add("MIFARE sectors without a known key: " + locked.joinToString(", "))
        }
    }

    private class AuthResult(val keyHex: String, val withKeyB: Boolean)

    private fun authenticate(
        classic: MifareClassic,
        sector: Int,
        keys: List<String>
    ): AuthResult? {
        for (keyHex in keys) {
            val key = Hex.decode(keyHex)
            try {
                if (classic.authenticateSectorWithKeyA(sector, key)) return AuthResult(keyHex, false)
                if (classic.authenticateSectorWithKeyB(sector, key)) return AuthResult(keyHex, true)
            } catch (e: IOException) {
                // A refused authentication usually drops the RF session, reconnect before the
                // next attempt; if that fails the tag is gone.
                if (!reconnect(classic)) return null
            }
        }
        return null
    }

    private fun readSectorBlocks(
        classic: MifareClassic,
        sector: Int,
        auth: AuthResult
    ): List<String> {
        val firstBlock = classic.sectorToBlock(sector)
        val blockCount = classic.getBlockCountInSector(sector)
        val blocks = ArrayList<String>(blockCount)
        val key = Hex.decode(auth.keyHex)
        for (offset in 0 until blockCount) {
            var data: ByteArray? = null
            var attempt = 0
            while (data == null && attempt < READ_ATTEMPTS) {
                attempt++
                data = try {
                    classic.readBlock(firstBlock + offset)
                } catch (e: IOException) {
                    if (!reconnect(classic)) break
                    val restored = try {
                        if (auth.withKeyB) {
                            classic.authenticateSectorWithKeyB(sector, key)
                        } else {
                            classic.authenticateSectorWithKeyA(sector, key)
                        }
                    } catch (e: IOException) {
                        false
                    }
                    if (!restored) break
                    null
                }
            }
            blocks.add(if (data == null) "" else Hex.encode(data))
        }
        return blocks
    }

    /** Key B sits in the last six bytes of the sector trailer and is sometimes readable. */
    private fun rememberTrailerKeyB(dump: SectorDump) {
        val trailer = dump.blocks.lastOrNull() ?: return
        if (trailer.length != BLOCK_HEX_LENGTH) return
        val candidate = trailer.substring(20, 32)
        if (candidate == ZERO_KEY) return
        if (!Hex.isValid(candidate)) return
        if (dump.keyB.isEmpty()) dump.keyB = candidate
    }

    private fun reconnect(technology: TagTechnology): Boolean = try {
        if (technology.isConnected) technology.close()
        technology.connect()
        true
    } catch (e: Exception) {
        false
    }

    private fun close(technology: TagTechnology) {
        try {
            if (technology.isConnected) technology.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot close the tag connection", e)
        }
    }

    private fun runSafely(record: CardRecord, label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            note(record, label, e)
        }
    }

    private fun note(record: CardRecord, label: String, error: Throwable) {
        val message = error.message
        record.notes.add(
            label + ": " + error.javaClass.simpleName + if (message.isNullOrEmpty()) "" else " $message"
        )
    }

    private const val TAG = "TagCopier"
    private const val ISO_TIMEOUT_MS = 2000
    private const val MAX_AIDS = 12
    private const val CLASSIC_TIME_BUDGET_MS = 12_000L
    private const val READ_ATTEMPTS = 2
    private const val BLOCK_HEX_LENGTH = 32
    private const val ZERO_KEY = "000000000000"

    /** Ultralight memory sizes in pages (4 bytes each). */
    private const val PAGES_ULTRALIGHT = 16
    private const val PAGES_ULTRALIGHT_C = 48
    private const val PAGES_NTAG213 = 45
    private const val PAGES_NTAG215 = 135
    private const val PAGES_NTAG216 = 231
    private const val PAGES_DEFAULT = 48

    /** GET VERSION storage size codes of the NTAG21x family. */
    private const val STORAGE_NTAG213 = 0x0F
    private const val STORAGE_NTAG215 = 0x11
    private const val STORAGE_NTAG216 = 0x13

    private val GET_VERSION_COMMAND = byteArrayOf(0x60)
}
