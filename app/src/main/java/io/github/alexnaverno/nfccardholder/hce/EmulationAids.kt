package io.github.alexnaverno.nfccardholder.hce

/**
 * Application identifiers used by the app.
 *
 * [BASE] must stay in sync with `res/xml/apduservice.xml`: the manifest registers the same set so
 * that the service is reachable even when runtime AID registration is not permitted.
 */
object EmulationAids {

    /** `2PAY.SYS.DDF01` - proximity payment system environment. */
    const val PPSE = "325041592E5359532E4444463031"

    /** `1PAY.SYS.DDF01` - payment system environment, also used by Calypso transit cards. */
    const val PSE = "315041592E5359532E4444463031"

    /** `D2760000850100` - NFC Forum / DESFire style application. */
    const val DESFIRE = "D2760000850100"

    /** `D2760000850101` - NFC Forum Type 4 Tag application (NDEF over ISO-DEP). */
    const val TYPE4_NDEF = "D2760000850101"

    /** `F04E464341524448` - proprietary AID of this app ("NFCARDH"), useful for testing. */
    const val PROPRIETARY = "F04E464341524448"

    /** Statically and dynamically registered fallback AIDs. */
    val BASE: List<String> = listOf(PPSE, PSE, DESFIRE, TYPE4_NDEF, PROPRIETARY)

    /** AIDs that are probed with SELECT when a card is copied. */
    val PROBE: List<String> = BASE
}
