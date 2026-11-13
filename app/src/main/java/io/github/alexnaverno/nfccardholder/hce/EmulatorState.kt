package io.github.alexnaverno.nfccardholder.hce

import io.github.alexnaverno.nfccardholder.model.CardRecord

/**
 * Process wide state shared by the activity and [CardEmulatorService].
 *
 * The activity updates it when the user presses "transmit", the service reads it while answering
 * a reader. When the service is started in a fresh process it reloads the active card from disk.
 */
object EmulatorState {

    @Volatile
    var activeSlot: Int = -1
        private set

    @Volatile
    var engine: EmulationEngine? = null
        private set

    @Volatile
    var lastTapAt: Long = 0L
        private set

    /** Makes [card] the card the phone transmits. Pass a negative slot to stop transmitting. */
    @Synchronized
    fun update(slot: Int, card: CardRecord?) {
        if (slot < 0 || card == null) {
            activeSlot = -1
            engine = null
        } else {
            activeSlot = slot
            engine = EmulationEngine(card)
        }
    }

    @Synchronized
    fun clear() = update(-1, null)

    /** Called by the service when a reader selected an application successfully. */
    fun noteTap() {
        lastTapAt = System.currentTimeMillis()
    }
}
