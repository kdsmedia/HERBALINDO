package com.altomedia.herbalindo.core;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Helper format & utilitas umum. */
public final class Util {
    private Util() {}

    private static final Locale ID = new Locale("in", "ID");

    public static String rupiah(long value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(ID);
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(value).replace("Rp", "Rp").replace(",00", "");
    }

    public static String num(long value) {
        return NumberFormat.getNumberInstance(ID).format(value);
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Kunci tanggal yang sama seperti {@link #todayKey()} untuk waktu tertentu. */
    public static String dayKey(Date when) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(when);
    }

    public static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }

    public static String dateTime(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", ID).format(d);
        } catch (Exception e) { return iso == null ? "-" : iso; }
    }

    public static String dateOnly(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso);
            return new SimpleDateFormat("dd MMM yyyy", ID).format(d);
        } catch (Exception e) { return iso == null ? "-" : iso; }
    }

    public static String orderDay() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    public static String id(String prefix) {
        return prefix + "-" + System.currentTimeMillis() % 100000000L + "-" +
                UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.US);
    }

    public static boolean isEmail(String s) {
        return s != null && s.matches("^\\S+@\\S+\\.\\S+$");
    }

    public static boolean isPhone(String s) {
        return s != null && s.matches("^0\\d{8,13}$");
    }

    /** URL gambar harus memakai skema http atau https, bukan skema lain. */
    public static boolean isHttpUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.US);
        return t.startsWith("http://") || t.startsWith("https://");
    }
}