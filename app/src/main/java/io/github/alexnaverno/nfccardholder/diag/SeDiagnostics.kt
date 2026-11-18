package io.github.alexnaverno.nfccardholder.diag

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.se.omapi.SEService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lists the secure element terminals the device exposes through the Open Mobile API
 * (`android.se.omapi`, Android 9 and newer).
 *
 * An `eSE1` or `SIM1` terminal is the hardware path OEM wallets use to emulate MIFARE Classic
 * cards: the secure element hosts a MIFARE applet and the NFC controller routes the MIFARE
 * listen mode to it. Whether that applet accepts a personalization from a third party app is
 * decided by its access rules and cannot be forced from here, so this class only reports what
 * the system exposes and whether a session and a basic channel can be opened at all.
 *
 * The callback is invoked on the main thread.
 */
class SeDiagnostics(private val context: Context) {

    /** One secure element terminal and how far a probe without credentials gets. */
    data class Terminal(
        val name: String,
        val sessionOpened: Boolean,
        /** `null` when the session could not be opened, otherwise "ok" or the failure reason. */
        val basicChannel: String?
    )

    data class Report(
        val supported: Boolean,
        val terminals: List<Terminal>,
        val timedOut: Boolean = false
    )

    fun run(callback: (Report) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback(Report(supported = false, terminals = emptyList()))
            return
        }
        val settled = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({
            if (settled.compareAndSet(false, true)) {
                executor.shutdown()
                callback(Report(supported = true, terminals = emptyList(), timedOut = true))
            }
        }, TIMEOUT_MS)

        var created: SEService? = null
        created = SEService(context, executor) {
            val service = created ?: return@SEService
            val report = probe(service)
            runCatching { service.shutdown() }
            executor.shutdown()
            main.post {
                if (settled.compareAndSet(false, true)) callback(report)
            }
        }
    }

    private fun probe(service: SEService): Report {
        val terminals = mutableListOf<Terminal>()
        for (reader in service.readers) {
            val name = runCatching { reader.name }.getOrDefault("?")
            var sessionOpened = false
            var channel: String? = null
            try {
                val session = reader.openSession()
                sessionOpened = true
                channel = try {
                    // A basic channel without an AID selects the default applet; opening and
                    // closing it is the deepest probe that needs no credentials.
                    session.openBasicChannel(null)?.close()
                    CHANNEL_OK
                } catch (e: Exception) {
                    reason(e)
                }
                runCatching { session.close() }
            } catch (e: Exception) {
                channel = reason(e)
            }
            terminals.add(Terminal(name, sessionOpened, channel))
        }
        return Report(supported = true, terminals = terminals)
    }

    private fun reason(error: Exception): String =
        error.javaClass.simpleName + if (error.message.isNullOrBlank()) "" else ": ${error.message}"

    companion object {
        private const val TIMEOUT_MS = 6000L
        private const val CHANNEL_OK = "ok"
    }
}
