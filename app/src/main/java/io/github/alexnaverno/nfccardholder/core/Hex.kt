package io.github.alexnaverno.nfccardholder.core

/**
 * Hexadecimal helpers.
 *
 * Pure Kotlin (no Android imports) so that it can be covered by JVM unit tests.
 */
object Hex {

    private const val DIGITS = "0123456789ABCDEF"

    /** Encodes bytes as an upper case hex string, e.g. `0A1F`. Null/empty becomes `""`. */
    fun encode(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            out.append(DIGITS[(b.toInt() ushr 4) and 0x0F])
            out.append(DIGITS[b.toInt() and 0x0F])
        }
        return out.toString()
    }

    /** Same as [encode] but with a single space between bytes, e.g. `0A 1F`. */
    fun encodeSpaced(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val out = StringBuilder(bytes.size * 3)
        for (i in bytes.indices) {
            if (i > 0) out.append(' ')
            out.append(DIGITS[(bytes[i].toInt() ushr 4) and 0x0F])
            out.append(DIGITS[bytes[i].toInt() and 0x0F])
        }
        return out.toString()
    }

    /**
     * Decodes a hex string. Whitespace, `:`, `-` and `_` separators as well as a leading `0x`
     * are ignored, letter case does not matter.
     *
     * @throws IllegalArgumentException when the input contains a non hex character or has an odd
     * number of digits.
     */
    fun decode(text: String?): ByteArray {
        if (text == null) return ByteArray(0)
        val cleaned = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() || c == ':' || c == '-' || c == '_' -> Unit
                c == '0' && cleaned.isEmpty() && i + 1 < text.length &&
                    (text[i + 1] == 'x' || text[i + 1] == 'X') -> i++
                else -> cleaned.append(c)
            }
            i++
        }
        if (cleaned.isEmpty()) return ByteArray(0)
        if (cleaned.length % 2 != 0) {
            throw IllegalArgumentException("Odd number of hex digits: \"$text\"")
        }
        val out = ByteArray(cleaned.length / 2)
        for (index in out.indices) {
            val high = Character.digit(cleaned[index * 2], 16)
            val low = Character.digit(cleaned[index * 2 + 1], 16)
            if (high < 0 || low < 0) {
                throw IllegalArgumentException("Not a hexadecimal string: \"$text\"")
            }
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    /** True when [decode] would succeed. */
    fun isValid(text: String?): Boolean = try {
        decode(text)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    fun isBlank(text: String?): Boolean = text == null || text.isBlank()

    /** Shortens a hex string for one-line UI display: `04A1B2C3D4E5F6` -> `04A1..F6`. */
    fun shorten(hex: String, head: Int = 4, tail: Int = 2): String = when {
        hex.length <= head + tail -> hex
        else -> hex.substring(0, head) + ".." + hex.substring(hex.length - tail)
    }
}
