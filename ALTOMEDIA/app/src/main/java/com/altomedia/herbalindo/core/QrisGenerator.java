package com.altomedia.herbalindo.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Penyusun payload QRIS dinamis (EMVCo TLV) beserta CRC16-CCITT/FALSE.
 * Nominal dimasukkan ke tag 54 dan checksum tag 63 dihitung ulang.
 */
public final class QrisGenerator {
    private QrisGenerator() {}

    public static class Tlv {
        public String tag, value;
        Tlv(String t, String v) { tag = t; value = v; }
    }

    public static List<Tlv> parse(String payload) {
        List<Tlv> out = new ArrayList<>();
        int i = 0;
        while (i + 4 <= payload.length()) {
            String tag = payload.substring(i, i + 2);
            int len;
            try { len = Integer.parseInt(payload.substring(i + 2, i + 4)); }
            catch (NumberFormatException e) { break; }
            int start = i + 4;
            int end = start + len;
            if (end > payload.length()) break;
            out.add(new Tlv(tag, payload.substring(start, end)));
            i = end;
        }
        return out;
    }

    /** Menghasilkan payload QRIS dengan nominal (rupiah) tertentu. */
    public static String build(String basePayload, long amountRupiah) {
        StringBuilder sb = new StringBuilder();
        List<Tlv> tlv = parse(basePayload);
        boolean pointOfInitiationWritten = false;
        for (Tlv t : tlv) {
            if ("54".equals(t.tag) || "63".equals(t.tag)) continue;
            if ("01".equals(t.tag)) {
                // 12 = dinamis (nominal menyertai), 11 = statis
                sb.append("01").append("02").append(amountRupiah > 0 ? "12" : "11");
                pointOfInitiationWritten = true;
                continue;
            }
            sb.append(t.tag).append(String.format(java.util.Locale.US, "%02d", t.value.length())).append(t.value);
        }
        if (!pointOfInitiationWritten) sb.append("0102").append(amountRupiah > 0 ? "12" : "11");
        if (amountRupiah > 0) {
            String amt = String.valueOf(amountRupiah);
            sb.append("54").append(String.format(java.util.Locale.US, "%02d", amt.length())).append(amt);
        }
        sb.append("6304");
        String crc = crc16(sb.toString());
        sb.append(crc);
        return sb.toString();
    }

    /**
     * Payload QRIS dinamis dengan nomor pesanan disisipkan pada tag 62
     * (additional data) sebagai referensi pembayaran.
     */
    public static String buildForOrder(String basePayload, long amountRupiah, String orderNumber) {
        String dynamic = build(rewriteTag62(basePayload, orderNumber), amountRupiah);
        return dynamic;
    }

    private static String rewriteTag62(String basePayload, String orderNumber) {
        String ref = "01" + String.format(java.util.Locale.US, "%02d", orderNumber.length()) + orderNumber;
        String t62 = "62" + String.format(java.util.Locale.US, "%02d", ref.length()) + ref;
        StringBuilder sb = new StringBuilder();
        for (Tlv t : parse(basePayload)) {
            if ("62".equals(t.tag) || "63".equals(t.tag)) continue;
            sb.append(t.tag).append(String.format(java.util.Locale.US, "%02d", t.value.length())).append(t.value);
        }
        sb.append(t62);
        sb.append("6304");
        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    /** CRC16-CCITT (poly 0x1021, init 0xFFFF) — standar EMVCo. */
    public static String crc16(String data) {
        int crc = 0xFFFF;
        for (int i = 0; i < data.length(); i++) {
            crc ^= (data.charAt(i) & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x8000) != 0) crc = (crc << 1) ^ 0x1021;
                else crc <<= 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(java.util.Locale.US, "%04X", crc);
    }

    /** Memvalidasi checksum payload QRIS. */
    public static boolean validate(String payload) {
        if (payload == null || payload.length() < 8) return false;
        int idx = payload.lastIndexOf("6304");
        if (idx < 0) return false;
        String body = payload.substring(0, idx + 4);
        String given = payload.substring(idx + 4);
        return crc16(body).equalsIgnoreCase(given);
    }

    public static String amountOf(String payload) {
        for (Tlv t : parse(payload)) if ("54".equals(t.tag)) return t.value;
        return "";
    }

    public static String merchantNameOf(String payload) {
        for (Tlv t : parse(payload)) if ("59".equals(t.tag)) return t.value;
        return "";
    }
}