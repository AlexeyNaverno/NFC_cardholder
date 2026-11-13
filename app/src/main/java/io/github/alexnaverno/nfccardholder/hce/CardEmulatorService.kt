package io.github.alexnaverno.nfccardholder.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.model.CardVault
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Host card emulation entry point. The system binds this service when a reader selects an AID that
 * is routed to the app and hands every command APDU to [processCommandApdu].
 *
 * The answers must be produced as fast as possible, therefore no disk access happens here: the
 * active card is kept in memory by [EmulatorState] and only the tap counter is written back
 * asynchronously once the link is lost.
 */
class CardEmulatorService : HostApduService() {

    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        ensureEngine()
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        ensureEngine()
        val engine = EmulatorState.engine
        if (engine == null) {
            Log.w(TAG, "APDU received while no card is active")
            return Apdu.SW_FILE_NOT_FOUND
        }
        val response = engine.respond(commandApdu)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                TAG,
                "C-APDU " + Hex.encodeSpaced(commandApdu) + " -> R-APDU " + Hex.encodeSpaced(response)
            )
        }
        return response
    }

    override fun onDeactivated(reason: Int) {
        val engine = EmulatorState.engine
        if (engine == null) return
        val slot = EmulatorState.activeSlot
        val taps = engine.taps
        engine.reset()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Deactivated, reason=$reason, answered=$taps")
        }
        if (taps <= 0 || slot < 0) return
        EmulatorState.noteTap()
        val application = applicationContext
        io.execute {
            try {
                CardVault.get(application).registerTaps(slot, taps)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot store the tap counter", e)
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** Reloads the active card from disk when the service was started in a fresh process. */
    private fun ensureEngine() {
        if (EmulatorState.engine != null) return
        val store = CardVault.get(this)
        val slot = store.activeSlot
        if (slot >= 0) EmulatorState.update(slot, store.slot(slot))
    }

    companion object {
        private const val TAG = "CardEmulatorService"
    }
}
