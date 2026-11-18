package io.github.alexnaverno.nfccardholder.diag

import io.github.alexnaverno.nfccardholder.core.Hex

/**
 * Builds a ready to run `adb` recipe that patches the NFC controller configuration so the phone
 * presents a chosen NFC-A listen UID.
 *
 * The listen UID is NCI parameter `LA_NFCID1`, stored in the controller start up configuration as
 * the TLV `33 <length> <uid bytes>` (inside `NXP_CORE_CONF` of `libnfc-nxp.conf` or
 * `NFA_DM_START_UP_CFG` of the Broadcom configs). With a fixed listen UID the phone answers the
 * NFC-A anticollision exactly like a UID-only MIFARE Classic card, which is the only MIFARE
 * "emulation" a rooted device can prepare without touching a secure element.
 *
 * The recipe is text for the user to run from a computer; the app never writes system files
 * itself. Pure Kotlin, covered by JVM unit tests.
 */
object ListenUidPatch {

    /** Controller config files known to carry the listen configuration, in probe order. */
    val CONFIG_PATHS: List<String> = listOf(
        "/vendor/etc/libnfc-nxp.conf",
        "/system/etc/libnfc-nxp.conf",
        "/vendor/etc/libnfc-nci.conf",
        "/system/etc/libnfc-nci.conf",
        "/vendor/etc/libnfc-brcm.conf",
        "/system/etc/libnfc-brcm.conf"
    )

    /**
     * Returns the UID as a list of unsigned bytes, or null when [uidHex] is not a hex string of
     * four or seven bytes (the two NFCID1 lengths NCI supports).
     */
    fun parse(uidHex: String?): List<Int>? {
        if (uidHex == null || !Hex.isValid(uidHex)) return null
        val bytes = Hex.decode(uidHex)
        if (bytes.size != UID_4_BYTES && bytes.size != UID_7_BYTES) return null
        return bytes.map { it.toInt() and 0xFF }
    }

    /** The whole recipe as shell comments plus `adb` commands, safe to paste into a terminal. */
    fun script(uidHex: String, label: String): String {
        val safeLabel = label.replace('\n', ' ').replace('\r', ' ')
        val bytes = parse(uidHex)
        if (bytes == null) {
            return buildString {
                appendLine(HEADER)
                appendLine("# Slot: $safeLabel")
                appendLine("# ERROR: \"$uidHex\" is not a 4 or 7 byte UID, there is nothing to patch.")
            }
        }
        val length = hex2(bytes.size)
        val value = bytes.joinToString(", ") { hex2(it) }
        // The replacement keeps the NCI parameter tag: 33 <length> <uid bytes>.
        val tlv = "33, $length, $value"
        val sedPattern = "33, *$length, *" + bytes.indices.drop(1)
            .joinToString(", *") { "[0-9A-Fa-f]{2}" }
        val grepPattern = ("33, *" + bytes.joinToString(", *") { hex2(it) })

        return buildString {
            appendLine(HEADER)
            appendLine("# Slot: $safeLabel, UID ${Hex.encode(Hex.decode(uidHex))}")
            appendLine("# The phone will answer the NFC-A anticollision with this UID in listen mode.")
            appendLine("# That covers readers which compare the UID only; readers that authenticate")
            appendLine("# MIFARE sectors still need an eSE/UICC applet (see the SE diagnostics screen).")
            appendLine("# Requires adb root (userdebug/eng build) or on-device root (Magisk). Payment")
            appendLine("# and transit over the secure element may stop working while patched.")
            appendLine()
            appendLine("adb root")
            appendLine("adb remount || adb shell su -c \"mount -o remount,rw /vendor || mount -o remount,rw /system\"")
            appendLine()
            appendLine("# 1. Back up every controller config once.")
            for (path in CONFIG_PATHS) {
                appendLine("adb shell su -c \"cp -a $path /data/local/tmp/${basename(path)}.bak\" || true")
            }
            appendLine()
            appendLine("# 2. Replace the LA_NFCID1 TLV (33 <length> <uid>) in place.")
            for (path in CONFIG_PATHS) {
                appendLine("adb shell su -c \"sed -i -E 's/$sedPattern/$tlv/g' $path\" || true")
            }
            appendLine()
            appendLine("# 3. Confirm the new TLV is present in at least one config.")
            for (path in CONFIG_PATHS) {
                appendLine("adb shell su -c \"grep -n -E '$grepPattern' $path\" || true")
            }
            appendLine()
            appendLine("# 4. Restart NFC so the controller reloads the configuration.")
            appendLine("adb shell svc nfc disable && adb shell svc nfc enable")
            appendLine()
            appendLine("# 5. Verify with a second phone: the UID of this phone must now be")
            appendLine("#    ${Hex.encode(Hex.decode(uidHex))}.")
            appendLine()
            appendLine("# If no config contained the TLV yet, add \"$tlv,\" inside its NXP_CORE_CONF or")
            appendLine("# NFA_DM_START_UP_CFG block (keep the block length byte consistent), then repeat 3-4.")
            appendLine()
            appendLine("# Restore:")
            appendLine("# adb shell su -c \"cp -a /data/local/tmp/libnfc-nxp.conf.bak /vendor/etc/libnfc-nxp.conf\"")
            appendLine("# adb shell svc nfc disable && adb shell svc nfc enable")
        }
    }

    private fun basename(path: String): String = path.substringAfterLast('/')

    private fun hex2(value: Int): String = Hex.encode(byteArrayOf(value.toByte()))

    private const val UID_4_BYTES = 4
    private const val UID_7_BYTES = 7

    private const val HEADER =
        "# NFC Cardholder: adb recipe for the NFC-A listen UID (LA_NFCID1)."
}
