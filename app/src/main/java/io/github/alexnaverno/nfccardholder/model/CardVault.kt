package io.github.alexnaverno.nfccardholder.model

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * The ten card slots, persisted as a single JSON document inside the app private storage.
 *
 * The file holds copied card data (UIDs, dumps, keys) and is never backed up to the cloud:
 * the manifest sets `android:allowBackup="false"`.
 */
class CardStore(context: Context) {

    private val appContext: Context = context.applicationContext

    val slots: Array<CardRecord?> = arrayOfNulls(SLOT_COUNT)

    /** Index of the slot that the phone currently transmits, or -1. */
    var activeSlot: Int = -1
        private set

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    fun load(): CardStore {
        val target = file
        if (!target.exists()) return this
        try {
            val json = JSONObject(target.readText(Charsets.UTF_8))
            val active = CardJson.vaultFromJson(json, slots)
            activeSlot = if (active in 0 until SLOT_COUNT && slots[active] != null) active else -1
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read $FILE_NAME, starting with empty slots", e)
            for (index in slots.indices) slots[index] = null
            activeSlot = -1
        }
        return this
    }

    @Synchronized
    fun save() {
        val target = file
        try {
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(CardJson.vaultToJson(slots, activeSlot).toString(), Charsets.UTF_8)
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "Cannot replace $FILE_NAME")
            }
            if (!tmp.renameTo(target)) {
                Log.w(TAG, "Cannot rename $FILE_NAME.tmp")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save $FILE_NAME", e)
        }
    }

    fun slot(index: Int): CardRecord? = if (index in 0 until SLOT_COUNT) slots[index] else null

    fun put(index: Int, record: CardRecord?) {
        if (index in 0 until SLOT_COUNT) slots[index] = record
    }

    fun remove(index: Int) {
        if (index !in 0 until SLOT_COUNT) return
        slots[index] = null
        if (activeSlot == index) activeSlot = -1
    }

    fun setActive(index: Int) {
        activeSlot = if (index in -1 until SLOT_COUNT) index else -1
    }

    fun countFilled(): Int = slots.count { it != null }

    /** Adds [count] successful reader taps to the slot and persists the file. Called off the main thread. */
    fun registerTaps(index: Int, count: Int) {
        if (count <= 0) return
        val record = slot(index) ?: return
        record.taps += count
        save()
    }

    companion object {
        const val SLOT_COUNT = 10
        const val FILE_NAME = "cards.json"
        private const val TAG = "CardStore"
    }
}

/**
 * Process wide access point to the [CardStore].
 *
 * The activity and the host card emulation service live in the same process, so sharing one
 * instance keeps them in sync; the service also reloads from disk when it is started on its own.
 */
object CardVault {

    @Volatile
    private var store: CardStore? = null

    fun get(context: Context): CardStore {
        store?.let { return it }
        synchronized(this) {
            val existing = store
            if (existing != null) return existing
            val created = CardStore(context.applicationContext).load()
            store = created
            return created
        }
    }

    /** Forces the next [get] to re-read the file from disk. */
    @Synchronized
    fun invalidate() {
        store = null
    }
}
