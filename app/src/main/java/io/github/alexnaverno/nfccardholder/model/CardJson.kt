package io.github.alexnaverno.nfccardholder.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON (de)serialisation for [CardRecord] and the whole vault.
 *
 * Uses `org.json`, which is part of the Android platform, so the app has no third party
 * serialisation dependency.
 */
object CardJson {

    const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_SLOTS = "slots"
    private const val KEY_ACTIVE = "activeSlot"

    private const val KEY_NAME = "name"
    private const val KEY_UID = "uid"
    private const val KEY_TECHS = "techs"
    private const val KEY_ATQA = "atqa"
    private const val KEY_SAK = "sak"
    private const val KEY_ISO_DEP = "isoDep"
    private const val KEY_HISTORICAL = "historicalBytes"
    private const val KEY_HI_LAYER = "hiLayerResponse"
    private const val KEY_NDEF_HEX = "ndefHex"
    private const val KEY_NDEF_TEXT = "ndefText"
    private const val KEY_MIFARE_TYPE = "mifareType"
    private const val KEY_MIFARE_SIZE = "mifareSize"
    private const val KEY_SECTORS_READ = "sectorsRead"
    private const val KEY_SECTORS_TOTAL = "sectorsTotal"
    private const val KEY_SECTORS = "sectors"
    private const val KEY_UL_PAGES = "ultralightPages"
    private const val KEY_UL_HEX = "ultralightHex"
    private const val KEY_CREATED = "createdAt"
    private const val KEY_TAPS = "taps"
    private const val KEY_AIDS = "aids"
    private const val KEY_PAIRS = "pairs"
    private const val KEY_NOTES = "notes"

    private const val KEY_AID = "aid"
    private const val KEY_RESPONSE = "response"
    private const val KEY_SOURCE = "source"
    private const val KEY_REQUEST = "request"

    private const val KEY_INDEX = "index"
    private const val KEY_KEY_A = "keyA"
    private const val KEY_KEY_B = "keyB"
    private const val KEY_BLOCKS = "blocks"

    fun recordToJson(record: CardRecord): JSONObject {
        val techs = JSONArray()
        record.techs.forEach { techs.put(it) }

        val aids = JSONArray()
        record.aids.forEach { entry ->
            aids.put(
                JSONObject()
                    .put(KEY_AID, entry.aid)
                    .put(KEY_RESPONSE, entry.response)
                    .put(KEY_SOURCE, entry.source)
            )
        }

        val pairs = JSONArray()
        record.pairs.forEach { pair ->
            pairs.put(
                JSONObject()
                    .put(KEY_REQUEST, pair.request)
                    .put(KEY_RESPONSE, pair.response)
            )
        }

        val sectors = JSONArray()
        record.sectors.forEach { sector ->
            val blocks = JSONArray()
            sector.blocks.forEach { blocks.put(it) }
            sectors.put(
                JSONObject()
                    .put(KEY_INDEX, sector.index)
                    .put(KEY_KEY_A, sector.keyA)
                    .put(KEY_KEY_B, sector.keyB)
                    .put(KEY_BLOCKS, blocks)
            )
        }

        val notes = JSONArray()
        record.notes.forEach { notes.put(it) }

        return JSONObject()
            .put(KEY_NAME, record.name)
            .put(KEY_UID, record.uid)
            .put(KEY_TECHS, techs)
            .put(KEY_ATQA, record.atqa)
            .put(KEY_SAK, record.sak)
            .put(KEY_ISO_DEP, record.isoDep)
            .put(KEY_HISTORICAL, record.historicalBytes)
            .put(KEY_HI_LAYER, record.hiLayerResponse)
            .put(KEY_NDEF_HEX, record.ndefHex)
            .put(KEY_NDEF_TEXT, record.ndefText)
            .put(KEY_MIFARE_TYPE, record.mifareType)
            .put(KEY_MIFARE_SIZE, record.mifareSize)
            .put(KEY_SECTORS_READ, record.sectorsRead)
            .put(KEY_SECTORS_TOTAL, record.sectorsTotal)
            .put(KEY_SECTORS, sectors)
            .put(KEY_UL_PAGES, record.ultralightPages)
            .put(KEY_UL_HEX, record.ultralightHex)
            .put(KEY_CREATED, record.createdAt)
            .put(KEY_TAPS, record.taps)
            .put(KEY_AIDS, aids)
            .put(KEY_PAIRS, pairs)
            .put(KEY_NOTES, notes)
    }

    fun recordFromJson(json: JSONObject): CardRecord {
        val record = CardRecord()
        record.name = json.optString(KEY_NAME, "")
        record.uid = json.optString(KEY_UID, "")
        record.techs = stringList(json.optJSONArray(KEY_TECHS))
        record.atqa = json.optString(KEY_ATQA, "")
        record.sak = json.optString(KEY_SAK, "")
        record.isoDep = json.optBoolean(KEY_ISO_DEP, false)
        record.historicalBytes = json.optString(KEY_HISTORICAL, "")
        record.hiLayerResponse = json.optString(KEY_HI_LAYER, "")
        record.ndefHex = json.optString(KEY_NDEF_HEX, "")
        record.ndefText = json.optString(KEY_NDEF_TEXT, "")
        record.mifareType = json.optString(KEY_MIFARE_TYPE, "")
        record.mifareSize = json.optInt(KEY_MIFARE_SIZE, 0)
        record.sectorsRead = json.optInt(KEY_SECTORS_READ, 0)
        record.sectorsTotal = json.optInt(KEY_SECTORS_TOTAL, 0)
        record.ultralightPages = json.optInt(KEY_UL_PAGES, 0)
        record.ultralightHex = json.optString(KEY_UL_HEX, "")
        record.createdAt = json.optLong(KEY_CREATED, 0L)
        record.taps = json.optInt(KEY_TAPS, 0)

        json.optJSONArray(KEY_AIDS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val aid = item.optString(KEY_AID, "")
                if (aid.isEmpty()) continue
                record.aids.add(
                    AidEntry(
                        aid,
                        item.optString(KEY_RESPONSE, ""),
                        item.optString(KEY_SOURCE, AidEntry.SOURCE_IMPORTED)
                    )
                )
            }
        }

        json.optJSONArray(KEY_PAIRS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val request = item.optString(KEY_REQUEST, "")
                if (request.isEmpty()) continue
                record.pairs.add(ApduPair(request, item.optString(KEY_RESPONSE, "")))
            }
        }

        json.optJSONArray(KEY_SECTORS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val blocks = mutableListOf<String>()
                item.optJSONArray(KEY_BLOCKS)?.let { blockArray ->
                    for (k in 0 until blockArray.length()) {
                        blocks.add(blockArray.optString(k, ""))
                    }
                }
                record.sectors.add(
                    SectorDump(
                        item.optInt(KEY_INDEX, i),
                        item.optString(KEY_KEY_A, ""),
                        item.optString(KEY_KEY_B, ""),
                        blocks
                    )
                )
            }
        }

        record.notes.addAll(stringList(json.optJSONArray(KEY_NOTES)))
        return record
    }

    fun vaultToJson(slots: Array<CardRecord?>, activeSlot: Int): JSONObject {
        val array = JSONArray()
        for (index in slots.indices) {
            val record = slots[index]
            array.put(if (record == null) JSONObject.NULL else recordToJson(record))
        }
        return JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_ACTIVE, activeSlot)
            .put(KEY_SLOTS, array)
    }

    /**
     * Fills [slots] from [json]. Slots that are missing or unreadable stay null.
     *
     * @return the persisted active slot index, or -1.
     */
    fun vaultFromJson(json: JSONObject, slots: Array<CardRecord?>): Int {
        val array = json.optJSONArray(KEY_SLOTS)
        if (array != null) {
            for (index in slots.indices) {
                if (index >= array.length()) break
                val item = array.optJSONObject(index)
                slots[index] = if (item == null) null else recordFromJson(item)
            }
        }
        return json.optInt(KEY_ACTIVE, -1)
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) out.add(array.optString(i, ""))
        return out
    }
}
