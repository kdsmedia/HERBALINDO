package com.altomedia.herbalindo.util

import java.util.Locale

/**
 * Builds a dynamic QRIS payload from the static merchant payload by injecting
 * tag 54 (Transaction Amount) in its EMVCo-mandated position — directly after
 * tag 53 (Transaction Currency) — and recomputing the CRC-16/CCITT-FALSE checksum.
 *
 * Inserting tag 54 in the correct position (rather than appending it) keeps the
 * payload compliant so banking apps and e-wallets accept the generated QR.
 */
object QrisEncoder {

    /** Static QRIS payload of ALTOMEDIA, Grosir (Karawang), including its CRC. */
    const val BASE_PAYLOAD =
        "00020101021126610014COM.GO-JEK.WWW01189360091439663050810210G9663050810303UMI51440014ID" +
            ".CO.QRIS.WWW0215ID10254671365660303UMI5204549953033605802ID5917ALTOMEDIA, Grosir6008" +
            "KARAWANG61054136162070703A016304D21A"

    private const val TAG_AMOUNT = "54"
    private const val TAG_CRC = "63"

    /** Largest amount tag 54 can carry in practice (12 significant digits). */
    const val MAX_AMOUNT = 999_999_999_999L

    fun crc16(data: String): String {
        var crc = 0xFFFF
        for (ch in data) {
            crc = crc xor (ch.code shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return String.format(Locale.US, "%04X", crc and 0xFFFF)
    }

    /** Splits a TLV string into (tag, value) pairs; malformed input yields what parsed so far. */
    private fun parseTlv(payload: String): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i + 4 <= payload.length) {
            val tag = payload.substring(i, i + 2)
            val len = payload.substring(i + 2, i + 4).toIntOrNull() ?: return out
            val start = i + 4
            val end = start + len
            if (end > payload.length) return out
            out.add(tag to payload.substring(start, end))
            i = end
        }
        return out
    }

    private fun serialize(tlv: List<Pair<String, String>>): String = buildString {
        for ((tag, value) in tlv) {
            append(tag)
            append(value.length.toString().padStart(2, '0'))
            append(value)
        }
    }

    /**
     * @return QRIS payload encoding [amount] in Rupiah, or null when [amount] is
     *         outside the range the standard can represent.
     */
    fun build(amount: Long): String? {
        if (amount <= 0 || amount > MAX_AMOUNT) return null

        val stripped = parseTlv(BASE_PAYLOAD).filter { it.first != TAG_CRC && it.first != TAG_AMOUNT }
        val withAmount = mutableListOf<Pair<String, String>>()
        var inserted = false
        for (entry in stripped) {
            withAmount.add(entry)
            if (entry.first == "53") {
                withAmount.add(TAG_AMOUNT to amount.toString())
                inserted = true
            }
        }
        if (!inserted) withAmount.add(TAG_AMOUNT to amount.toString())

        val ready = serialize(withAmount) + TAG_CRC + "04"
        return ready + crc16(ready)
    }
}