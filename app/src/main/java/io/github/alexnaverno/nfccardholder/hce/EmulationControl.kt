package io.github.alexnaverno.nfccardholder.hce

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Log
import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.model.CardRecord
import io.github.alexnaverno.nfccardholder.model.CardVault

/**
 * Glue between the UI and Android card emulation: decides which card is transmitted and which AIDs
 * the phone answers for.
 */
object EmulationControl {

    enum class Result {
        /** The card is transmitted and all of its AIDs were registered for this service. */
        ACTIVATED,

        /**
         * The card is transmitted, but the AIDs of the card could not be registered at runtime
         * (the app is not the default service for the category). Only the AIDs from
         * `res/xml/apduservice.xml` are routed to the app by the system.
         */
        ACTIVATED_BASE_AIDS_ONLY,

        NO_NFC,
        NFC_DISABLED,
        NO_HCE,
        NOT_EMULATABLE,
        STOPPED
    }

    @Suppress("DEPRECATION")
    fun adapter(context: Context): NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(context)
    } catch (e: Exception) {
        Log.w(TAG, "No NFC adapter", e)
        null
    }

    fun hceSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    fun service(context: Context): ComponentName =
        ComponentName(context, CardEmulatorService::class.java)

    fun cardEmulation(context: Context): CardEmulation? = try {
        adapter(context)?.let { CardEmulation.getInstance(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Card emulation is unavailable on this device", e)
        null
    }

    /** True when the system routes the `other` category to this app. */
    fun isDefaultForOther(context: Context): Boolean {
        val emulation = cardEmulation(context) ?: return false
        return try {
            emulation.isDefaultServiceForCategory(service(context), CardEmulation.CATEGORY_OTHER)
        } catch (e: Exception) {
            false
        }
    }

    /** AIDs currently registered at runtime; statically registered ones are not reported. */
    fun registeredAids(context: Context): List<String> {
        val emulation = cardEmulation(context) ?: return emptyList()
        return try {
            emulation.getAidsForService(service(context), CardEmulation.CATEGORY_OTHER)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Every AID the phone should answer for while [record] is transmitted: the static base list
     * plus the AIDs of the card. Runtime registration replaces the whole list for a category, so
     * the base AIDs must always be part of it.
     */
    fun aidsFor(record: CardRecord): List<String> {
        val all = LinkedHashSet<String>()
        EmulationAids.BASE.forEach { all.add(it.uppercase()) }
        record.aids.forEach { entry ->
            val aid = entry.aid.trim().uppercase()
            if (Apdu.isValidAid(aid)) all.add(aid)
        }
        if (record.ndefHex.isNotEmpty()) all.add(EmulationAids.TYPE4_NDEF)
        return all.toList()
    }

    /** How many of the AIDs of [record] are longer or shorter than Android allows. */
    fun rejectedAids(record: CardRecord): List<String> =
        record.aids.map { it.aid }.filter { !Apdu.isValidAid(it) }

    fun activate(context: Context, slot: Int): Result {
        val store = CardVault.get(context)
        val record = store.slot(slot) ?: return Result.NOT_EMULATABLE
        if (!record.canEmulate()) return Result.NOT_EMULATABLE
        if (!hceSupported(context)) return Result.NO_HCE
        val nfc = adapter(context) ?: return Result.NO_NFC
        if (!nfc.isEnabled) return Result.NFC_DISABLED

        EmulatorState.update(slot, record)
        store.setActive(slot)
        val registered = register(context, aidsFor(record))
        store.save()

        return if (registered) Result.ACTIVATED else Result.ACTIVATED_BASE_AIDS_ONLY
    }

    fun stop(context: Context): Result {
        val store = CardVault.get(context)
        val application = context.applicationContext
        EmulatorState.clear()
        store.setActive(-1)
        val emulation = cardEmulation(application)
        if (emulation != null) {
            try {
                emulation.removeAidsForService(
                    service(application),
                    CardEmulation.CATEGORY_OTHER
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cannot unregister AIDs", e)
            }
        }
        store.save()
        return Result.STOPPED
    }

    private fun register(context: Context, aids: List<String>): Boolean {
        val emulation = cardEmulation(context) ?: return false
        if (aids.isEmpty()) return false
        return try {
            emulation.registerAidsForService(
                service(context),
                CardEmulation.CATEGORY_OTHER,
                aids
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "AID registration refused, the app is not the default service", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "AID registration failed", e)
            false
        }
    }

    private const val TAG = "EmulationControl"
}
